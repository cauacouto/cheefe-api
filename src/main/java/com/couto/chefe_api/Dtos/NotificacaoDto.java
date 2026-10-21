package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.domin.Notificacao;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record NotificacaoDto(
        Long id,
        EventoNotificacao evento,
        String mensagem,
        String referenciaId,
        boolean lida,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
        LocalDateTime criadaEm
) {

    public static NotificacaoDto de(Notificacao notificacao) {
        return new NotificacaoDto(notificacao.getId(), notificacao.getEvento(), notificacao.getMensagem(),
                notificacao.getReferenciaId(), notificacao.isLida(), notificacao.getCriadaEm());
    }
}
