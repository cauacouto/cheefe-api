package com.couto.chefe_api.dto;

import java.time.DayOfWeek;
import java.util.List;

/** A agenda do próprio chef: bloqueios que ainda não terminaram (por início) e dias fixos da semana. */
public record AgendaChefeDto(List<BloqueioAgendaDto> bloqueios, List<DayOfWeek> diasFixos) {
}
