package com.couto.chefe_api.Services.eventos;

import java.util.UUID;

/** total = não lidas do destinatário, contadas na mesma transação em que a notificação foi criada. */
public record NotificacaoCriadaEvent(UUID destinatarioId, long total) {
}
