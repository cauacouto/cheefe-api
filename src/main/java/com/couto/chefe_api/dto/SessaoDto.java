package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.TipoUsuario;

import java.util.UUID;

public record SessaoDto(
        UUID id,
        String email,
        TipoUsuario tipo
) {
}
