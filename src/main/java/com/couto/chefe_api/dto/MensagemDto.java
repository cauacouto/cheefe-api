package com.couto.chefe_api.dto;

import com.couto.chefe_api.domain.Mensagem;
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
        boolean apagada,
        /** Só para o autor: o front explica que telefone, e-mail e redes ficam ocultos no ChefHome. */
        boolean contatoOculto
) {

    public static MensagemDto de(Mensagem mensagem, UUID leitorId) {
        boolean apagada = mensagem.isApagada();
        boolean minha = mensagem.getRemetenteId().equals(leitorId);
        return new MensagemDto(mensagem.getId(), mensagem.getConversa().getId(), apagada ? null : mensagem.getTexto(),
                mensagem.getEnviadaEm(), minha, mensagem.getLidaEm() != null,
                mensagem.getLidaEm(), mensagem.getEditadaEm() != null, apagada,
                minha && !apagada && mensagem.isContatoOculto());
    }
}
