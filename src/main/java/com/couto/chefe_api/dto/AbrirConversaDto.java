package com.couto.chefe_api.dto;

import java.util.UUID;

/** Cliente manda só {@code chefeId}; chef manda só {@code clienteId} (qualquer outra combinação é 400, ver ChatService). */
public record AbrirConversaDto(UUID chefeId, UUID clienteId) {
}
