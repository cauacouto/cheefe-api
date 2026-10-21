package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.enums.TipoUsuario;

import java.util.UUID;

public record SessaoDto(
        UUID id,
        String email,
        TipoUsuario tipo
) {
}
