package com.couto.chefe_api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.util.List;

/** Lista completa dos dias fixos (substitui a anterior); vazia limpa. Repetição é validada no serviço. */
public record DiasFixosRequestDto(@NotNull List<@NotNull DayOfWeek> dias) {
}
