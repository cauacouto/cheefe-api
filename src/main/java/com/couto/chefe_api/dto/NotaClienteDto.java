package com.couto.chefe_api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/** A nota do chef como ficou, depois do filtro. */
public record NotaClienteDto(
        String texto,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime atualizadaEm
) {
}
