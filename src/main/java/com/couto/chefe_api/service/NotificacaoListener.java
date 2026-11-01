package com.couto.chefe_api.service;

import com.couto.chefe_api.enums.MotivoAgendamento;
import com.couto.chefe_api.service.eventos.AgendamentoExpiradoEvent;
import com.couto.chefe_api.service.eventos.ChefSaiuDoAgendamentoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoCanceladoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoConfirmadoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoCriadoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoEditadoEvent;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendamentoRepository;
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

    private static final int TAMANHO_MENSAGEM = 300;
    private static final int TAMANHO_DESCRICAO = 120;
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
        String nomeChef = agendamento.getChefe().getNome();
        notificacaoService.notificar(agendamento.getUsuario().getId(), TipoUsuario.CLIENTE,
                EventoNotificacao.AGENDAMENTO_CONFIRMADO,
                "Chef " + nomeChef + " confirmou seu agendamento de " + dia(agendamento.getDataHoraInicial()),
                String.valueOf(agendamento.getId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void aoCancelar(AgendamentoCanceladoEvent evento) {
        Agendamento agendamento = buscar(evento.agendamentoId());
        String mensagem = agendamento.getUsuario().getNome() + " cancelou o agendamento de "
                + dia(agendamento.getDataHoraInicial()) + motivo(evento.motivo(), evento.descricao());
        notificarChefs(agendamento, EventoNotificacao.AGENDAMENTO_CANCELADO, mensagem);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void aoChefSair(ChefSaiuDoAgendamentoEvent evento) {
        Agendamento agendamento = buscar(evento.agendamentoId());
        String dia = dia(agendamento.getDataHoraInicial());
        String mensagem = evento.recusa()
                ? "Seu pedido de " + dia + " foi recusado"
                : "O agendamento de " + dia + " foi cancelado: o chef " + evento.nomeChef() + " desistiu";
        notificacaoService.notificar(agendamento.getUsuario().getId(), TipoUsuario.CLIENTE,
                evento.recusa() ? EventoNotificacao.AGENDAMENTO_RECUSADO : EventoNotificacao.CHEF_DESISTIU,
                caber(mensagem + motivo(evento.motivo(), evento.descricao())), String.valueOf(agendamento.getId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void aoExpirar(AgendamentoExpiradoEvent evento) {
        Agendamento agendamento = buscar(evento.agendamentoId());
        notificacaoService.notificar(agendamento.getUsuario().getId(), TipoUsuario.CLIENTE, EventoNotificacao.AGENDAMENTO_EXPIRADO,
                "Seu pedido de " + dia(agendamento.getDataHoraInicial()) + " expirou sem confirmação",
                String.valueOf(agendamento.getId()));
    }

    /** ": {rótulo}" e/ou " — {descrição}" (encurtada: o texto completo fica no histórico). */
    private static String motivo(MotivoAgendamento motivo, String descricao) {
        String texto = motivo == null ? "" : ": " + motivo.rotulo();
        if (descricao != null && !descricao.isBlank()) {
            String limpa = descricao.strip();
            texto += " — " + (limpa.length() <= TAMANHO_DESCRICAO ? limpa : limpa.substring(0, TAMANHO_DESCRICAO) + "…");
        }
        return texto;
    }

    /** A notificação guarda até 300 caracteres. */
    private static String caber(String mensagem) {
        return mensagem.length() <= TAMANHO_MENSAGEM ? mensagem : mensagem.substring(0, TAMANHO_MENSAGEM - 1) + "…";
    }

    private void notificarChefs(Agendamento agendamento, EventoNotificacao evento, String mensagem) {
        notificacaoService.notificar(agendamento.getChefe().getId(), TipoUsuario.CHEFE, evento, caber(mensagem),
                String.valueOf(agendamento.getId()));
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
