package com.couto.chefe_api.Services.eventos;

import com.couto.chefe_api.Dtos.MensagemDto;

import java.util.UUID;

public record MensagemEditadaEvent(UUID autorId, MensagemDto paraAutor, UUID outroId, MensagemDto paraOutro) {
}
