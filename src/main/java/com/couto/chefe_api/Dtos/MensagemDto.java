package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.domin.Mensagem;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

/** Mensagem do ponto de vista de quem lê: "minha" é relativo a esse leitor; apagada não expõe o texto. */
public record MensagemDto(
        Long id,
        Long conversaId,
        String texto,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime enviadaEm,
        boolean minha,
        boolean lida,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime lidaEm,
        boolean editada,
        boolean apagada
) {

    public static MensagemDto de(Mensagem mensagem, UUID leitorId) {
        boolean apagada = mensagem.isApagada();
        return new MensagemDto(mensagem.getId(), mensagem.getConversa().getId(), apagada ? null : mensagem.getTexto(),
                mensagem.getEnviadaEm(), mensagem.getRemetenteId().equals(leitorId), mensagem.getLidaEm() != null,
                mensagem.getLidaEm(), mensagem.getEditadaEm() != null, apagada);
    }
}
