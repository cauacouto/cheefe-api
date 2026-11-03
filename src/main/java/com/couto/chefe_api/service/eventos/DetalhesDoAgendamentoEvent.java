package com.couto.chefe_api.service.eventos;

/** O cliente confirmou os detalhes do agendamento, ou os alterou ({@code alterados}). */
public record DetalhesDoAgendamentoEvent(Long agendamentoId, boolean alterados) {
}
