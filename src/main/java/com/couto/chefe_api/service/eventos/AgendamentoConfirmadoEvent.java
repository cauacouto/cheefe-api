package com.couto.chefe_api.service.eventos;

import java.util.UUID;

public record AgendamentoConfirmadoEvent(Long agendamentoId, UUID chefeId) {
}
