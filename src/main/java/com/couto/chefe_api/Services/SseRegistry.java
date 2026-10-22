package com.couto.chefe_api.Services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Conexões SSE abertas, por usuário (uma por aba). Fica em memória: com mais de uma instância da API,
 * cada uma só alcança as abas conectadas nela.
 */
@Slf4j
@Component
public class SseRegistry {

    static final long TIMEOUT = Duration.ofMinutes(30).toMillis();
    /** Abas por usuário; a mais antiga é fechada quando passa disso (evita acumular conexões esquecidas). */
    static final int MAXIMO_CONEXOES_POR_USUARIO = 5;

    private final Map<UUID, Deque<SseEmitter>> conexoes = new ConcurrentHashMap<>();

    public SseEmitter abrir(UUID usuarioId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        emitter.onCompletion(() -> remover(usuarioId, emitter));
        emitter.onTimeout(() -> {
            remover(usuarioId, emitter);
            emitter.complete();
        });
        emitter.onError(erro -> remover(usuarioId, emitter));
        registrar(usuarioId, emitter);
        return emitter;
    }

    void registrar(UUID usuarioId, SseEmitter emitter) {
        Deque<SseEmitter> abas = conexoes.computeIfAbsent(usuarioId, id -> new ConcurrentLinkedDeque<>());
        abas.addLast(emitter);
        while (abas.size() > MAXIMO_CONEXOES_POR_USUARIO) {
            SseEmitter maisAntiga = abas.pollFirst();
            if (maisAntiga != null) {
                maisAntiga.complete();
            }
        }
    }

    /** Entrega a todas as abas do usuário; a que falhar é descartada. Nunca lança exceção. */
    public void enviar(UUID usuarioId, String evento, Object dados) {
        Deque<SseEmitter> abas = conexoes.get(usuarioId);
        if (abas == null) {
            return;
        }
        for (SseEmitter emitter : abas) {
            entregar(usuarioId, emitter, SseEmitter.event().name(evento).data(dados, MediaType.APPLICATION_JSON));
        }
    }

    /** Mantém as conexões vivas atrás de proxies e descobre as que caíram. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        conexoes.forEach((usuarioId, abas) ->
                abas.forEach(emitter -> entregar(usuarioId, emitter, SseEmitter.event().comment("ping"))));
    }

    int conexoes(UUID usuarioId) {
        Deque<SseEmitter> abas = conexoes.get(usuarioId);
        return abas == null ? 0 : abas.size();
    }

    private void entregar(UUID usuarioId, SseEmitter emitter, SseEmitter.SseEventBuilder evento) {
        try {
            emitter.send(evento);
        } catch (Exception e) {
            log.debug("conexão SSE descartada: {}", e.getMessage());
            remover(usuarioId, emitter);
            emitter.completeWithError(e);
        }
    }

    private void remover(UUID usuarioId, SseEmitter emitter) {
        conexoes.computeIfPresent(usuarioId, (id, abas) -> {
            abas.remove(emitter);
            return abas.isEmpty() ? null : abas;
        });
    }
}
