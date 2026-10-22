package com.couto.chefe_api.service;

import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.AgendamentoHistorico;
import com.couto.chefe_api.enums.AcaoAgendamento;
import com.couto.chefe_api.enums.MotivoAgendamento;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendamentoHistoricoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/** Grava a linha do tempo do agendamento (só inserção). Autor nulo = o sistema. */
@Service
@RequiredArgsConstructor
public class HistoricoAgendamentoService {

    private final AgendamentoHistoricoRepository repository;
    private final Clock clock;

    @Transactional
    public void registrar(Agendamento agendamento, AcaoAgendamento acao, UUID autorId, TipoUsuario autorTipo,
                          MotivoAgendamento motivo, String descricao) {
        AgendamentoHistorico linha = new AgendamentoHistorico();
        linha.setAgendamento(agendamento);
        linha.setAcao(acao);
        linha.setAutorId(autorId);
        linha.setAutorTipo(autorTipo);
        linha.setMotivo(motivo == null ? null : motivo.name());
        linha.setDescricao(descricao == null || descricao.isBlank() ? null : descricao.strip());
        linha.setOcorridoEm(LocalDateTime.now(clock));
        repository.save(linha);
    }
}
