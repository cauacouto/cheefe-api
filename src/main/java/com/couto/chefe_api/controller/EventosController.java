package com.couto.chefe_api.controller;

import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.service.SseRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Canal de tempo real (mensagens do chat e contagem de notificações) do usuário logado. */
@RestController
@RequiredArgsConstructor
public class EventosController {

    private final SseRegistry sseRegistry;

    @GetMapping(value = "/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventos(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return sseRegistry.abrir(usuario.id());
    }
}
