package com.couto.chefe_api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Período em que o chef não atende. As regras de início/fim e de duração são validadas no serviço. */
public record BloqueioAgendaRequestDto(
        @NotNull @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime inicio,
        @NotNull @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime fim
) {
}
