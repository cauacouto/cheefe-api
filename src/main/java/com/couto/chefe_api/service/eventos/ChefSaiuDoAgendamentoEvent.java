package com.couto.chefe_api.service.eventos;

import com.couto.chefe_api.enums.MotivoAgendamento;

/** O chef recusou (recusa=true) ou desistiu (false); o agendamento continua com ele. */
public record ChefSaiuDoAgendamentoEvent(Long agendamentoId, String nomeChef, boolean recusa,
                                         MotivoAgendamento motivo, String descricao) {
}
