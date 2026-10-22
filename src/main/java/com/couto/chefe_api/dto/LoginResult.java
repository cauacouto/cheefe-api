package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.TipoUsuario;

public record LoginResult(
        String accessToken,
        String registroToken,
        TipoUsuario tipo
) {
}
