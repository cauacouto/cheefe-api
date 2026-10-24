package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.TipoUsuario;

import java.util.UUID;

public record SessaoDto(
        UUID id,
        String email,
        TipoUsuario tipo,
        /** Falso: o front abre a tela do SMS antes de agendar, conversar ou avaliar. Nulo para a moderação. */
        Boolean telefoneVerificado
) {
}
