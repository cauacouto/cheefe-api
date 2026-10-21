package com.couto.chefe_api.Services.eventos;

import java.util.UUID;

public record AgendamentoConfirmadoEvent(Long agendamentoId, UUID chefeId) {
}
