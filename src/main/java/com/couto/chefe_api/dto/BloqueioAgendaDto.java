package com.couto.chefe_api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record BloqueioAgendaDto(
        Long id,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime inicio,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime fim
) {
}
