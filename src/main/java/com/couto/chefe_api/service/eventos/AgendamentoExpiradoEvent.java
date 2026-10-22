package com.couto.chefe_api.service.eventos;

/** O pedido chegou ao horário sem nenhum chef confirmar (rotina). */
public record AgendamentoExpiradoEvent(Long agendamentoId) {
}
