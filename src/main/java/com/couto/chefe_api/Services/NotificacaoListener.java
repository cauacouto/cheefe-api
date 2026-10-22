package com.couto.chefe_api.Services;

import com.couto.chefe_api.Services.eventos.AgendamentoConfirmadoEvent;
import com.couto.chefe_api.Services.eventos.AgendamentoCriadoEvent;
import com.couto.chefe_api.Services.eventos.AgendamentoEditadoEvent;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.AgendamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Grava as notificações na mesma transação da ação (BEFORE_COMMIT): se o agendamento
 * falhar, não há notificação; se a notificação falhar, o agendamento também não é gravado.
 */
@Component
@RequiredArgsConstructor
public class NotificacaoListener {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final String SEM_DATA = "data a definir";

    private final AgendamentoRepository agendamentoRepository;
    private final NotificacaoService notificacaoService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void aoCriar(AgendamentoCriadoEvent evento) {
        Agendamento agendamento = buscar(evento.agendamentoId());
        String mensagem = agendamento.getUsuario().getNome() + " pediu um agendamento para "
                + diaEHora(agendamento.getDataHoraInicial());
        notificarChefs(agendamento, EventoNotificacao.AGENDAMENTO_CRIADO, mensagem);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void aoEditar(AgendamentoEditadoEvent evento) {
        Agendamento agendamento = buscar(evento.agendamentoId());
        String mensagem = agendamento.getUsuario().getNome() + " alterou o agendamento de "
                + dia(agendamento.getDataHoraInicial());
        notificarChefs(agendamento, EventoNotificacao.AGENDAMENTO_EDITADO, mensagem);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void aoConfirmar(AgendamentoConfirmadoEvent evento) {
        Agendamento agendamento = buscar(evento.agendamentoId());
        String nomeChef = agendamento.getChefes().stream()
                .filter(chefe -> chefe.getId().equals(evento.chefeId()))
                .map(ChefeModel::getNome)
                .findFirst()
                .orElse("");
        notificacaoService.notificar(agendamento.getUsuario().getId(), TipoUsuario.CLIENTE,
                EventoNotificacao.AGENDAMENTO_CONFIRMADO,
                "Chef " + nomeChef + " confirmou seu agendamento de " + dia(agendamento.getDataHoraInicial()),
                String.valueOf(agendamento.getId()));
    }

    private void notificarChefs(Agendamento agendamento, EventoNotificacao evento, String mensagem) {
        agendamento.getChefes().forEach(chefe -> notificacaoService.notificar(
                chefe.getId(), TipoUsuario.CHEFE, evento, mensagem, String.valueOf(agendamento.getId())));
    }

    // agendamentos antigos podem não ter data; a notificação não pode quebrar a ação
    private static String dia(LocalDateTime data) {
        return data == null ? SEM_DATA : data.format(DIA);
    }

    private static String diaEHora(LocalDateTime data) {
        return data == null ? SEM_DATA : data.format(DIA) + " às " + data.format(HORA);
    }

    private Agendamento buscar(Long agendamentoId) {
        return agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new IllegalStateException("agendamento " + agendamentoId + " sumiu antes do commit"));
    }
}
