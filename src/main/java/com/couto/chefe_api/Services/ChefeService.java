package com.couto.chefe_api.Services;

import com.couto.chefe_api.Chefe.ChefeSpecification;
import com.couto.chefe_api.Dtos.ChefeRequestDto;
import com.couto.chefe_api.Dtos.ChefeResponseDto;
import com.couto.chefe_api.Excepitons.ChefeNaoEncontradoException;
import com.couto.chefe_api.Mapper.ChefeMapper;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.repositorys.ChefeRepository;
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

    private final ChefeRepository chefeRepository;
    private final ChefeMapper chefeMapper;

    @Transactional
    public ChefeResponseDto atualizarDados(ChefeRequestDto dto, UUID id) {
        ChefeModel chefe = chefeRepository.findById(id).orElseThrow(ChefeNaoEncontradoException::new);
        chefe.exigirAtivo();
        chefeMapper.updateModelFromDto(dto, chefe);
        return chefeMapper.toDto(chefeRepository.save(chefe));
    }

    @Transactional(readOnly = true)
    public Page<ChefeResponseDto> mostrarChefes(Pageable pageable, BigDecimal valorMnimmo, BigDecimal valorMaximo) {
        Specification<ChefeModel> spec = ChefeSpecification.ativo()
                .and(ChefeSpecification.disponivel())
                .and(ChefeSpecification.valorMinimoMaior(valorMnimmo))
                .and(ChefeSpecification.valorMaximoMneor(valorMaximo));

        return chefeRepository.findAll(spec, pageable)
                .map(chefeMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<ChefeResponseDto> chefesMaisAgendados(Pageable pageable) {
        return chefeRepository.findMaisAgendados(pageable)
                .map(chefeMapper::toDto);
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
