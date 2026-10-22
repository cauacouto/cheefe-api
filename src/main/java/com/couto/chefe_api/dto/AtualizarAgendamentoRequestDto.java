package com.couto.chefe_api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Atualização parcial de agendamento: todos os campos são opcionais, mas os
 * que vierem precisam ser válidos.
 */
public record AtualizarAgendamentoRequestDto(

        @Future
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
        LocalDateTime dataHoraInicial,

        @Future
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
        LocalDateTime dataHoraFinal,

        Long enderecoId,

        @Size(min = 1)
        List<@NotNull UUID> chefeIds,

        @Positive
        Integer quantidadePessoas
) {
}
