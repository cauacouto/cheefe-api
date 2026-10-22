package com.couto.chefe_api.service.eventos;

import com.couto.chefe_api.dto.MensagemDto;

import java.util.UUID;

/** Já traz a mensagem vista por cada lado: o envio em tempo real (depois do commit) não volta ao banco. */
public record MensagemEnviadaEvent(UUID remetenteId, MensagemDto paraRemetente,
                                   UUID destinatarioId, MensagemDto paraDestinatario) {
}
