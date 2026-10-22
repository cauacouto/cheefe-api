package com.couto.chefe_api.service;

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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChefeService {

    static final int TAMANHO_MAXIMO_PAGINA = 50;
    static final Set<String> ORDENAVEIS = Set.of("nome", "valorHora", "anosDeExperiencia");

    private final ChefeRepository chefeRepository;
    private final ChefeMapper chefeMapper;
    private final DenunciaRepository denunciaRepository;

    @Transactional
    public ChefeResponseDto atualizarDados(ChefeRequestDto dto, UUID id) {
        ChefeModel chefe = chefeRepository.findById(id).orElseThrow(ChefeNaoEncontradoException::new);
        chefe.exigirAtivo();
        chefeMapper.updateModelFromDto(dto, chefe);
        return chefeMapper.toDto(chefeRepository.save(chefe));
    }

    @Transactional(readOnly = true)
    public ChefePerfilDto buscarPerfil(UUID chefeId) {
        ChefeModel chefe = chefeRepository.findById(chefeId).orElseThrow(ChefeNaoEncontradoException::new);
        long denunciantes = denunciaRepository.countDistinctClienteByChefeIdAndStatus(chefeId, StatusDenuncia.ABERTA);
        return ChefePerfilDto.de(chefe, denunciantes >= DenunciaService.LIMITE_OCULTACAO);
    }

    @Transactional(readOnly = true)
    public Page<ChefeResponseDto> mostrarChefes(Pageable pageable, BigDecimal valorMinimo, BigDecimal valorMaximo) {
        Specification<ChefeModel> spec = ChefeSpecification.ativo()
                .and(ChefeSpecification.disponivel())
                .and(ChefeSpecification.perfilCompleto())
                .and(ChefeSpecification.naoSuspenso())
                .and(ChefeSpecification.semDenunciasEmExcesso())
                .and(ChefeSpecification.valorMinimoMaior(valorMinimo))
                .and(ChefeSpecification.valorMaximoMenor(valorMaximo));

        return chefeRepository.findAll(spec, paginaDaBusca(pageable))
                .map(chefeMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<ChefeResponseDto> chefesMaisAgendados(Pageable pageable) {
        // a ordem é a da query (mais agendados primeiro); a do cliente é ignorada
        return chefeRepository.findMaisAgendados(PageRequest.of(pageable.getPageNumber(), tamanho(pageable)))
                .map(chefeMapper::toDto);
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
