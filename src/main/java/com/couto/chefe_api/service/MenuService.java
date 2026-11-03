package com.couto.chefe_api.service;

import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Menu;
import com.couto.chefe_api.domain.MenuItem;
import com.couto.chefe_api.domain.Pratos;
import com.couto.chefe_api.dto.MenuItemRequestDto;
import com.couto.chefe_api.dto.MenuRequestDto;
import com.couto.chefe_api.dto.MenuResponseDto;
import com.couto.chefe_api.exception.ChefeNaoEncontradoException;
import com.couto.chefe_api.exception.ConflitoException;
import com.couto.chefe_api.exception.ContatoNaoPermitidoException;
import com.couto.chefe_api.exception.MenuNaoEncontradoException;
import com.couto.chefe_api.exception.RegraDeNegocioException;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.MenuRepository;
import com.couto.chefe_api.repository.PratosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Menus do chef: pratos dele por etapa, a um preço por pessoa. Criar e editar travam o chef (mesma trava da
 * agenda) para dois cadastros simultâneos não passarem do limite. Menu não se exclui: pausa-se.
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    static final int MAXIMO_MENUS = 20;
    static final int PASSO_DURACAO_MINUTOS = 30;

    private final MenuRepository menuRepository;
    private final PratosRepository pratosRepository;
    private final ChefeRepository chefeRepository;
    private final DetectorContato detectorContato;
    private final Clock clock;

    @Transactional
    public MenuResponseDto criar(MenuRequestDto dto, UUID chefeId) {
        ChefeModel chefe = travarAtivo(chefeId);
        validar(dto);
        if (menuRepository.countByChefe_Id(chefeId) >= MAXIMO_MENUS) {
            throw new ConflitoException("Limite de " + MAXIMO_MENUS + " menus atingido");
        }
        Menu menu = new Menu();
        menu.setChefe(chefe);
        menu.setCriadoEm(LocalDateTime.now(clock));
        preencher(menu, dto, pratosDoChefe(dto, chefeId));
        return MenuResponseDto.de(menuRepository.save(menu));
    }

    @Transactional
    public MenuResponseDto editar(Long id, MenuRequestDto dto, UUID chefeId) {
        travarAtivo(chefeId);
        // de outro chef é tratado como inexistente
        Menu menu = menuRepository.findByIdAndChefe_Id(id, chefeId).orElseThrow(MenuNaoEncontradoException::new);
        validar(dto);
        preencher(menu, dto, pratosDoChefe(dto, chefeId));
        return MenuResponseDto.de(menuRepository.save(menu));
    }

    @Transactional
    public MenuResponseDto pausar(Long id, UUID chefeId) {
        return mudarAtivo(id, chefeId, false);
    }

    @Transactional
    public MenuResponseDto ativar(Long id, UUID chefeId) {
        return mudarAtivo(id, chefeId, true);
    }

    /** Todos os menus do chef, inclusive os pausados. */
    @Transactional(readOnly = true)
    public List<MenuResponseDto> meus(UUID chefeId) {
        chefeRepository.findById(chefeId).orElseThrow(ChefeNaoEncontradoException::new).exigirAtivo();
        return menuRepository.findByChefe_IdOrderByIdAsc(chefeId).stream().map(MenuResponseDto::de).toList();
    }

    /** Página pública: só os ativos; chef que não aparece na busca "não existe". */
    @Transactional(readOnly = true)
    public List<MenuResponseDto> doChefe(UUID chefeId) {
        if (!chefeRepository.visivel(chefeId)) {
            throw new ChefeNaoEncontradoException();
        }
        return menuRepository.findByChefe_IdAndAtivoTrueOrderByIdAsc(chefeId).stream().map(MenuResponseDto::de).toList();
    }

    private MenuResponseDto mudarAtivo(Long id, UUID chefeId, boolean ativo) {
        travarAtivo(chefeId);
        Menu menu = menuRepository.findByIdAndChefe_Id(id, chefeId).orElseThrow(MenuNaoEncontradoException::new);
        menu.setAtivo(ativo);
        return MenuResponseDto.de(menuRepository.save(menu));
    }

    /** Regras que a Bean Validation não cobre. Recusa contato em vez de mascarar: é formulário. */
    private void validar(MenuRequestDto dto) {
        if (dto.maximoConvidados() < dto.minimoConvidados()) {
            throw new RegraDeNegocioException("O máximo de convidados não pode ser menor que o mínimo");
        }
        if (dto.duracaoMinutos() % PASSO_DURACAO_MINUTOS != 0) {
            throw new RegraDeNegocioException("A duração deve ser múltipla de " + PASSO_DURACAO_MINUTOS + " minutos");
        }
        if (new HashSet<>(dto.ocasioes()).size() != dto.ocasioes().size()) {
            throw new RegraDeNegocioException("Ocasiões repetidas no menu");
        }
        if (dto.itens().stream().map(MenuItemRequestDto::pratoId).distinct().count() != dto.itens().size()) {
            throw new RegraDeNegocioException("O mesmo prato não pode aparecer duas vezes no menu");
        }
        if (detectorContato.contem(dto.nome()) || detectorContato.contem(dto.descricao())) {
            throw new ContatoNaoPermitidoException("menu");
        }
    }

    /** Os pratos do menu por id; prato de outro chef ou inexistente é recusado (400). */
    private Map<Integer, Pratos> pratosDoChefe(MenuRequestDto dto, UUID chefeId) {
        Set<Integer> ids = dto.itens().stream().map(MenuItemRequestDto::pratoId).collect(Collectors.toSet());
        Map<Integer, Pratos> pratos = pratosRepository.findAllById(ids).stream()
                .filter(p -> p.getChefeModel().getId().equals(chefeId))
                .collect(Collectors.toMap(Pratos::getId, Function.identity()));
        if (pratos.size() != ids.size()) {
            throw new RegraDeNegocioException("Só é possível usar pratos cadastrados por você no menu");
        }
        return pratos;
    }

    /** Substitui itens e ocasiões; o prato que continua no menu só muda de etapa e ordem (a chave é menu + prato). */
    private void preencher(Menu menu, MenuRequestDto dto, Map<Integer, Pratos> pratos) {
        menu.setNome(dto.nome().strip());
        menu.setDescricao(dto.descricao());
        menu.setPrecoPorPessoa(dto.precoPorPessoa().setScale(2));
        menu.setMinimoConvidados(dto.minimoConvidados());
        menu.setMaximoConvidados(dto.maximoConvidados());
        menu.setDuracaoMinutos(dto.duracaoMinutos());
        menu.setIngredientesInclusos(dto.ingredientesInclusos());
        menu.getOcasioes().clear();
        menu.getOcasioes().addAll(dto.ocasioes());

        Map<Integer, MenuItemRequestDto> novos = dto.itens().stream()
                .collect(Collectors.toMap(MenuItemRequestDto::pratoId, Function.identity()));
        menu.getItens().removeIf(i -> !novos.containsKey(i.getPrato().getId()));
        Map<Integer, MenuItem> atuais = menu.getItens().stream()
                .collect(Collectors.toMap(i -> i.getPrato().getId(), Function.identity()));
        novos.forEach((pratoId, novo) -> {
            MenuItem existente = atuais.get(pratoId);
            if (existente != null) {
                existente.reposicionar(novo.etapa(), novo.ordem());
            } else {
                menu.getItens().add(new MenuItem(menu, pratos.get(pratoId), novo.etapa(), novo.ordem()));
            }
        });
    }

    private ChefeModel travarAtivo(UUID chefeId) {
        ChefeModel chefe = chefeRepository.travar(List.of(chefeId)).stream().findFirst()
                .orElseThrow(ChefeNaoEncontradoException::new);
        chefe.exigirAtivo();
        return chefe;
    }
}
