package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.OcasiaoMenu;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Atualização parcial de agendamento: todos os campos são opcionais, mas os
 * que vierem precisam ser válidos. {@code restricoes} vazia limpa as restrições;
 * {@code observacoes} em branco limpa as observações.
 */
public record AtualizarAgendamentoRequestDto(

        @Future
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
        LocalDateTime dataHoraInicial,

        Long enderecoId,

        @Positive
        Integer quantidadePessoas,

        Long menuId,

        OcasiaoMenu ocasiao,

        @Valid
        List<RestricaoDto> restricoes,

        @Size(max = 1000)
        String observacoes
) {
}
