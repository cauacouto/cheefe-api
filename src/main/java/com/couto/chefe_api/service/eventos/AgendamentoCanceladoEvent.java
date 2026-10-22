package com.couto.chefe_api.service.eventos;

import com.couto.chefe_api.enums.MotivoAgendamento;

/** O cliente cancelou; os chefs do agendamento são avisados. */
public record AgendamentoCanceladoEvent(Long agendamentoId, MotivoAgendamento motivo, String descricao) {
}
