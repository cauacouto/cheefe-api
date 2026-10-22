package com.couto.chefe_api.Services;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SseRegistryTest {

    private final SseRegistry registry = new SseRegistry();
    private final UUID ana = UUID.randomUUID();

    @Test
    void duasAbasDoMesmoUsuarioRecebem() throws IOException {
        SseEmitter aba1 = mock(SseEmitter.class);
        SseEmitter aba2 = mock(SseEmitter.class);
        registry.registrar(ana, aba1);
        registry.registrar(ana, aba2);

        registry.enviar(ana, "mensagem", "oi");

        verify(aba1).send(any(SseEmitter.SseEventBuilder.class));
        verify(aba2).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void abaQueCaiuEhDescartadaEAsOutrasContinuam() throws IOException {
        SseEmitter caiu = mock(SseEmitter.class);
        SseEmitter viva = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(caiu).send(any(SseEmitter.SseEventBuilder.class));
        registry.registrar(ana, caiu);
        registry.registrar(ana, viva);

        assertThatCode(() -> registry.enviar(ana, "mensagem", "1")).doesNotThrowAnyException();
        registry.enviar(ana, "mensagem", "2");

        verify(caiu, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(viva, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(registry.conexoes(ana)).isEqualTo(1);
    }

    @Test
    void usuarioSemConexaoNaoAconteceNada() {
        assertThatCode(() -> registry.enviar(UUID.randomUUID(), "mensagem", "oi")).doesNotThrowAnyException();
    }

    @Test
    void heartbeatDescartaConexoesMortas() throws IOException {
        SseEmitter morta = mock(SseEmitter.class);
        doThrow(new IOException("closed")).when(morta).send(any(SseEmitter.SseEventBuilder.class));
        registry.registrar(ana, morta);

        registry.heartbeat();

        assertThat(registry.conexoes(ana)).isZero();
    }

    @Test
    void limiteDeAbasPorUsuarioFechaAMaisAntiga() {
        SseEmitter maisAntiga = mock(SseEmitter.class);
        registry.registrar(ana, maisAntiga);
        for (int i = 1; i < SseRegistry.MAXIMO_CONEXOES_POR_USUARIO; i++) {
            registry.registrar(ana, mock(SseEmitter.class));
        }

        registry.registrar(ana, mock(SseEmitter.class));

        verify(maisAntiga).complete();
        assertThat(registry.conexoes(ana)).isEqualTo(SseRegistry.MAXIMO_CONEXOES_POR_USUARIO);
    }

    @Test
    void abrirCriaEmitterComTimeoutDe30Minutos() {
        SseEmitter emitter = registry.abrir(ana);

        assertThat(emitter.getTimeout()).isEqualTo(30 * 60 * 1000L);
        assertThat(registry.conexoes(ana)).isEqualTo(1);
    }
}
