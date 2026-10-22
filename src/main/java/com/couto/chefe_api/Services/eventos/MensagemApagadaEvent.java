package com.couto.chefe_api.Services.eventos;

import java.util.UUID;

/** Apagada pelo autor ou removida pela moderação: os dois participantes são avisados. */
public record MensagemApagadaEvent(Long conversaId, Long mensagemId, UUID clienteId, UUID chefeId) {
}
