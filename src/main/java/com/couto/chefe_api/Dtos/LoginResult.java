package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.enums.TipoUsuario;

public record LoginResult(
        String accessToken,
        String registroToken,
        TipoUsuario tipo
) {
}
