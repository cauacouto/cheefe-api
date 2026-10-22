package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.TipoUsuario;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConversaDto(
        Long id,
        ParticipanteDto outroParticipante,
        UltimaMensagemDto ultimaMensagem,
        long naoLidas,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime ultimaMensagemEm
) {

    public record ParticipanteDto(UUID id, String nome, String imageUrl, TipoUsuario tipo) {
    }

    /** Prévia (até 100 caracteres); null quando a mensagem foi apagada. */
    public record UltimaMensagemDto(String texto, @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime enviadaEm,
                                    boolean minha, boolean apagada) {
    }
}
