package com.couto.chefe_api.service;

import com.couto.chefe_api.dto.ContagemDto;
import com.couto.chefe_api.dto.MensagemDto;
import com.couto.chefe_api.service.eventos.MensagemApagadaEvent;
import com.couto.chefe_api.service.eventos.MensagemEnviadaEvent;
import com.couto.chefe_api.service.eventos.MensagensLidasEvent;
import com.couto.chefe_api.service.eventos.NotificacoesAlteradasEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TempoRealListenerTest {

    private final SseRegistry registry = mock(SseRegistry.class);
    private final TempoRealListener listener = new TempoRealListener(registry);
    private final UUID ana = UUID.randomUUID();
    private final UUID bia = UUID.randomUUID();
    private final LocalDateTime agora = LocalDateTime.now();

    @Test
    void mensagemVaiParaODestinatarioEParaAsAbasDoRemetente() {
        MensagemDto minha = new MensagemDto(10L, 1L, "oi", agora, true, false, null, false, false);
        MensagemDto dela = new MensagemDto(10L, 1L, "oi", agora, false, false, null, false, false);

        listener.aoEnviar(new MensagemEnviadaEvent(ana, minha, bia, dela));

        verify(registry).enviar(bia, "mensagem", dela);
        verify(registry).enviar(ana, "mensagem", minha);
    }

    @Test
    void apagadaVaiParaOsDoisParticipantes() {
        listener.aoApagar(new MensagemApagadaEvent(1L, 10L, ana, bia));

        verify(registry).enviar(ana, "mensagem_apagada", new TempoRealListener.MensagemApagada(1L, 10L));
        verify(registry).enviar(bia, "mensagem_apagada", new TempoRealListener.MensagemApagada(1L, 10L));
    }

    @Test
    void vistoVaiParaQuemEnviouEParaAsAbasDeQuemLeu() {
        listener.aoLer(new MensagensLidasEvent(1L, bia, ana, 10L, agora));

        TempoRealListener.MensagensLidas visto = new TempoRealListener.MensagensLidas(1L, 10L, agora);
        verify(registry).enviar(ana, "mensagens_lidas", visto);
        verify(registry).enviar(bia, "mensagens_lidas", visto);
    }

    @Test
    void notificacaoCriadaEnviaAContagem() {
        listener.aoNotificar(new NotificacoesAlteradasEvent(bia, 3));

        verify(registry).enviar(bia, "notificacao", new ContagemDto(3));
    }

    @Test
    void falhaNoEnvioNuncaSobeParaQuemChamou() {
        doThrow(new IllegalStateException("emitter já fechado")).when(registry).enviar(any(), anyString(), any());

        assertThatCode(() -> listener.aoNotificar(new NotificacoesAlteradasEvent(bia, 1))).doesNotThrowAnyException();
        assertThatCode(() -> listener.aoApagar(new MensagemApagadaEvent(1L, 10L, ana, bia))).doesNotThrowAnyException();
    }
}
