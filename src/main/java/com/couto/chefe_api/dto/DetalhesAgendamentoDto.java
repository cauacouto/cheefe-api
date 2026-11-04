package com.couto.chefe_api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Novos detalhes de um agendamento confirmado: substituem os dois campos. O que não vier vale como vazio
 * (sem restrições, sem observações).
 */
public record DetalhesAgendamentoDto(

        List<@NotNull @Valid RestricaoDto> restricoes,

        @Size(max = 1000)
        String observacoes
) {
}
