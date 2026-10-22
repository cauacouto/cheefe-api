package com.couto.chefe_api.service.eventos;

import java.util.UUID;

/** Notificação criada ou lida; total = não lidas do destinatário, contadas na mesma transação. */
public record NotificacoesAlteradasEvent(UUID destinatarioId, long total) {
}
