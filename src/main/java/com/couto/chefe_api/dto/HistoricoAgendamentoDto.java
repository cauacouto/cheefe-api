package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.AcaoAgendamento;
import com.couto.chefe_api.enums.TipoUsuario;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

/** Uma linha da linha do tempo; autor nulo = o sistema, motivo nulo = sem motivo pré-definido. */
public record HistoricoAgendamentoDto(
        AcaoAgendamento acao,
        Autor autor,
        Motivo motivo,
        String descricao,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime ocorridoEm
) {

    public record Autor(UUID id, String nome, TipoUsuario tipo) {
    }

    public record Motivo(String codigo, String rotulo) {
    }
}
