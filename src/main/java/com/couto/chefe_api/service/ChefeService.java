package com.couto.chefe_api.service;

import com.couto.chefe_api.exception.RegraDeNegocioException;

import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.OcasiaoMenu;
import com.couto.chefe_api.repository.MenuRepository;

import com.couto.chefe_api.domain.Cidade;

import java.util.Set;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import com.couto.chefe_api.repository.ChefeSpecification;
import com.couto.chefe_api.dto.ChefePerfilDto;
import com.couto.chefe_api.dto.ChefeRequestDto;
import com.couto.chefe_api.dto.ChefeResponseDto;
import com.couto.chefe_api.exception.ChefeNaoEncontradoException;
import com.couto.chefe_api.mapper.ChefeMapper;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.DenunciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChefeService {

    static final int TAMANHO_MAXIMO_PAGINA = 50;
    static final String NOTA_MEDIA = "notaMedia";
    static final String PRECO_A_PARTIR_DE = "precoAPartirDe";
    static final Set<String> ORDENAVEIS = Set.of("nome", PRECO_A_PARTIR_DE, "anosDeExperiencia", NOTA_MEDIA);

    private final ChefeRepository chefeRepository;
    private final ChefeMapper chefeMapper;
    private final DenunciaRepository denunciaRepository;
    private final MenuRepository menuRepository;

    @Transactional
    public ChefeResponseDto atualizarDados(ChefeRequestDto dto, UUID id) {
        // trava o chef (como a confirmação de agendamento): confirmar e trocar a folga não se cruzam
        ChefeModel chefe = chefeRepository.travar(List.of(id)).stream().findFirst()
                .orElseThrow(ChefeNaoEncontradoException::new);
        chefe.exigirAtivo();
        exigirSemRepeticao(dto.especialidades());
        if (dto.folgaMinutos() != null && dto.folgaMinutos() % 15 != 0) {
            throw new RegraDeNegocioException("A folga deve ser múltipla de 15 minutos");
        }
        chefeMapper.updateModelFromDto(dto, chefe);
        ChefeResponseDto resposta = chefeMapper.toDto(chefeRepository.save(chefe));
        return ChefeResponseDto.comPrecoAPartirDe(resposta, menoresPrecos(List.of(id)).get(id));
    }

    @Transactional(readOnly = true)
    public ChefePerfilDto buscarPerfil(UUID chefeId) {
        ChefeModel chefe = chefeRepository.findById(chefeId).orElseThrow(ChefeNaoEncontradoException::new);
        long denunciantes = denunciaRepository.countDistinctClienteByChefeIdAndStatus(chefeId, StatusDenuncia.ABERTA);
        BigDecimal precoAPartirDe = menoresPrecos(List.of(chefeId)).get(chefeId);
        return ChefePerfilDto.de(chefe, denunciantes >= DenunciaService.LIMITE_OCULTACAO,
                menuRepository.existsByChefe_IdAndAtivoTrue(chefeId), precoAPartirDe);
    }

    @Transactional(readOnly = true)
    public Page<ChefeResponseDto> mostrarChefes(Pageable pageable, BigDecimal valorMinimo, BigDecimal valorMaximo,
                                                String cidade, String estado, Especialidades especialidade,
                                                OcasiaoMenu ocasiao) {
        String cidadeBusca = Cidade.normalizar(cidade);
        if (cidadeBusca != null && Cidade.uf(estado) == null) {
            // há cidades com o mesmo nome em estados diferentes
            throw new RegraDeNegocioException("Informe o estado (UF) junto com a cidade");
        }
        Specification<ChefeModel> spec = ChefeSpecification.ativo()
                .and(ChefeSpecification.disponivel())
                .and(ChefeSpecification.perfilCompleto())
                .and(ChefeSpecification.naoSuspenso())
                .and(ChefeSpecification.naoBloqueado())
                .and(ChefeSpecification.semDenunciasEmExcesso())
                .and(ChefeSpecification.comMenuAtivo(ocasiao, valorMinimo, valorMaximo))
                .and(ChefeSpecification.naCidade(cidadeBusca))
                .and(ChefeSpecification.noEstado(Cidade.uf(estado)))
                .and(ChefeSpecification.daEspecialidade(especialidade));

        PageRequest pagina = paginaDaBusca(pageable);
        if (pagina.getSort().getOrderFor(NOTA_MEDIA) != null) {
            // a média não é coluna: a ordem inteira vai para a Specification (sempre desc, "Novo" por último)
            // com os dois juntos, a nota vale e o preço é ignorado
            Sort demais = Sort.by(pagina.getSort().stream()
                    .filter(o -> !NOTA_MEDIA.equals(o.getProperty()) && !PRECO_A_PARTIR_DE.equals(o.getProperty())).toList());
            spec = spec.and(ChefeSpecification.ordenadoPorNotaMedia(demais));
            pagina = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize());
        } else if (pagina.getSort().getOrderFor(PRECO_A_PARTIR_DE) != null) {
            // o preço também não é coluna do chef: a ordem inteira vai para a Specification
            Sort.Direction direcao = pagina.getSort().getOrderFor(PRECO_A_PARTIR_DE).getDirection();
            Sort demais = Sort.by(pagina.getSort().stream().filter(o -> !PRECO_A_PARTIR_DE.equals(o.getProperty())).toList());
            spec = spec.and(ChefeSpecification.ordenadoPorPreco(direcao, demais));
            pagina = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize());
        }
        return comPrecos(chefeRepository.findAll(spec, pagina).map(chefeMapper::toDto));
    }

    @Transactional(readOnly = true)
    public Page<ChefeResponseDto> chefesMaisAgendados(Pageable pageable) {
        // a ordem é a da query (mais agendados primeiro); a do cliente é ignorada
        return comPrecos(chefeRepository.findMaisAgendados(PageRequest.of(pageable.getPageNumber(), tamanho(pageable)))
                .map(chefeMapper::toDto));
    }

    /** O preço a partir de vem dos menus, numa consulta só para a página inteira. */
    private Page<ChefeResponseDto> comPrecos(Page<ChefeResponseDto> pagina) {
        Map<UUID, BigDecimal> precos = menoresPrecos(pagina.getContent().stream().map(ChefeResponseDto::id).toList());
        return pagina.map(chefe -> ChefeResponseDto.comPrecoAPartirDe(chefe, precos.get(chefe.id())));
    }

    private Map<UUID, BigDecimal> menoresPrecos(Collection<UUID> chefes) {
        if (chefes.isEmpty()) {
            return Map.of();
        }
        return menuRepository.menoresPrecos(chefes).stream()
                .collect(Collectors.toMap(MenuRepository.MenorPreco::getChefeId, MenuRepository.MenorPreco::getMinimo));
    }

    /** Especialidade repetida na lista é erro do pedido, não algo para o Set engolir em silêncio. */
    public static void exigirSemRepeticao(List<Especialidades> especialidades) {
        if (especialidades != null && new HashSet<>(especialidades).size() != especialidades.size()) {
            throw new RegraDeNegocioException("Especialidade repetida");
        }
    }

    /** Ordenação só por campos que a própria resposta mostra; qualquer outro (ex.: e-mail) é descartado. */
    private static PageRequest paginaDaBusca(Pageable pageable) {
        List<Sort.Order> permitidas = pageable.getSort().stream()
                .filter(ordem -> ORDENAVEIS.contains(ordem.getProperty()))
                .toList();
        return PageRequest.of(pageable.getPageNumber(), tamanho(pageable), Sort.by(permitidas));
    }

    private static int tamanho(Pageable pageable) {
        return Math.max(1, Math.min(pageable.getPageSize(), TAMANHO_MAXIMO_PAGINA));
    }

    /**
     * Apagar a conta: soft delete (ativo=false). Um chefe com agendamentos ou pratos não pode
     * ser apagado de fato (FK), e o histórico precisa continuar existindo. Conta apagada não
     * loga nem opera.
     */
    @Transactional
    public void deletar(UUID id) {
        ChefeModel chefe = chefeRepository.findById(id).orElseThrow(ChefeNaoEncontradoException::new);
        chefe.setAtivo(false);
        // LGPD: o telefone verificado é dado da conta e some com ela (o hash de banido, se houver, fica)
        chefe.setTelefoneVerificado(null);
        chefe.setTelefoneVerificadoEm(null);
        chefeRepository.save(chefe);
    }

    /** Pausa: some da busca e não recebe agendamentos novos, mas a conta continua operando. */
    @Transactional
    public void inativar(UUID id) {
        alterarDisponibilidade(id, false);
    }

    @Transactional
    public void ativar(UUID id) {
        alterarDisponibilidade(id, true);
    }

    private void alterarDisponibilidade(UUID id, boolean disponivel) {
        ChefeModel chefe = chefeRepository.findById(id).orElseThrow(ChefeNaoEncontradoException::new);
        chefe.exigirAtivo();
        chefe.setDisponivel(disponivel);
        chefeRepository.save(chefe);
    }
}
