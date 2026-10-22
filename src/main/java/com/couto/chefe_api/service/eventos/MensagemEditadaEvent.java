package com.couto.chefe_api.service.eventos;

import com.couto.chefe_api.dto.MensagemDto;

import java.util.UUID;

public record MensagemEditadaEvent(UUID autorId, MensagemDto paraAutor, UUID outroId, MensagemDto paraOutro) {
}
