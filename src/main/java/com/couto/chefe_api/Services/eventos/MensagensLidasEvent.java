package com.couto.chefe_api.Services.eventos;

import java.time.LocalDateTime;
import java.util.UUID;

/** O leitor leu as mensagens recebidas até ateId; o remetente delas recebe o "visto". */
public record MensagensLidasEvent(Long conversaId, UUID leitorId, Long ateId, LocalDateTime lidaEm) {
}
