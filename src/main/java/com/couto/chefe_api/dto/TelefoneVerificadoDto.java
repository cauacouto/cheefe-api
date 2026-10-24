package com.couto.chefe_api.dto;

import java.time.LocalDateTime;

/** O telefone como ficou gravado (E.164) e quando foi verificado. */
public record TelefoneVerificadoDto(String telefone, LocalDateTime verificadoEm) {
}
