package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.ContagemDto;
import com.couto.chefe_api.Services.eventos.MensagemApagadaEvent;
import com.couto.chefe_api.Services.eventos.MensagemEditadaEvent;
import com.couto.chefe_api.Services.eventos.MensagemEnviadaEvent;
import com.couto.chefe_api.Services.eventos.MensagensLidasEvent;
import com.couto.chefe_api.Services.eventos.NotificacaoCriadaEvent;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Empurra pelo SSE só depois do commit: ninguém vê o que acabou desfeito. Uma falha aqui não pode
 * chegar a quem fez a requisição (a mensagem já está gravada), por isso tudo é engolido e logado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TempoRealListener {

    private final SseRegistry sseRegistry;

    public record MensagemApagada(Long conversaId, Long mensagemId) {
    }

    public record MensagensLidas(Long conversaId, Long ateId,
                                 @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime lidaEm) {
    }

    /** O remetente também recebe: as outras abas dele mostram a mensagem (o front ignora o id repetido). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoEnviar(MensagemEnviadaEvent evento) {
        enviar(evento.destinatarioId(), "mensagem", evento.paraDestinatario());
        enviar(evento.remetenteId(), "mensagem", evento.paraRemetente());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoEditar(MensagemEditadaEvent evento) {
        enviar(evento.outroId(), "mensagem_editada", evento.paraOutro());
        enviar(evento.autorId(), "mensagem_editada", evento.paraAutor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoApagar(MensagemApagadaEvent evento) {
        MensagemApagada dados = new MensagemApagada(evento.conversaId(), evento.mensagemId());
        enviar(evento.clienteId(), "mensagem_apagada", dados);
        enviar(evento.chefeId(), "mensagem_apagada", dados);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoLer(MensagensLidasEvent evento) {
        MensagensLidas dados = new MensagensLidas(evento.conversaId(), evento.ateId(), evento.lidaEm());
        enviar(evento.outroId(), "mensagens_lidas", dados);
        enviar(evento.leitorId(), "mensagens_lidas", dados);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoNotificar(NotificacaoCriadaEvent evento) {
        enviar(evento.destinatarioId(), "notificacao", new ContagemDto(evento.total()));
    }

    private void enviar(UUID usuarioId, String evento, Object dados) {
        try {
            sseRegistry.enviar(usuarioId, evento, dados);
        } catch (RuntimeException e) {
            log.warn("falha ao enviar evento SSE {}: {}", evento, e.getMessage());
        }
    }
}
