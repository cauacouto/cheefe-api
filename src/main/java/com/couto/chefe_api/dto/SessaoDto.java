package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.TipoUsuario;

import java.util.UUID;

public record SessaoDto(
        UUID id,
        String email,
        TipoUsuario tipo,
        /** Só informação enquanto a verificação de telefone está parada (nada exige). Nulo para a moderação. */
        Boolean telefoneVerificado
) {
}
