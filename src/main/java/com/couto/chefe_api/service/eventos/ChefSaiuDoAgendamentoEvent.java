package com.couto.chefe_api.service.eventos;

import com.couto.chefe_api.enums.MotivoAgendamento;

/**
 * Um chef recusou (recusa=true) ou desistiu (false). O nome vem no evento porque, quando o listener
 * roda, o chef já saiu da lista de chefs do agendamento.
 */
public record ChefSaiuDoAgendamentoEvent(Long agendamentoId, String nomeChef, boolean recusa, boolean restamChefes,
                                         MotivoAgendamento motivo, String descricao) {
}
