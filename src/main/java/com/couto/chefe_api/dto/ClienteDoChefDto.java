package com.couto.chefe_api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

/** Linha de "meus clientes": só o primeiro nome e os agendamentos {@code CONFIRMADO} ou {@code CONCLUIDO} com este chef. */
public record ClienteDoChefDto(
        UUID id,
        String primeiroNome,
        String foto,
        long totalAgendamentos,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime ultimoAgendamento,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime proximoAgendamento
) {

    /** Primeira palavra do nome: é o que o chef vê do cliente fora do pedido. */
    public static String primeiroNome(String nome) {
        String limpo = nome == null ? "" : nome.strip();
        int espaco = limpo.indexOf(' ');
        return espaco < 0 ? limpo : limpo.substring(0, espaco);
    }
}
