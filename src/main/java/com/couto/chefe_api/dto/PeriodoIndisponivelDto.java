package com.couto.chefe_api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/** Consulta pública: só quando o chef não atende, sem dizer o motivo (bloqueio, dia fixo ou agendamento). */
public record PeriodoIndisponivelDto(
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime inicio,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime fim
) {
}
