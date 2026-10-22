package com.couto.chefe_api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Criação de agendamento. A regra "fim depois do início" é validada no serviço.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgendamentoResquestDto {

    @NotNull
    @Future
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime dataHoraInicial;

    @NotNull
    @Future
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime dataHoraFinal;

    @NotNull
    private Long enderecoId;

    @NotEmpty
    private List<@NotNull UUID> chefeIds;

    @NotNull
    @Positive
    private Integer quantidadePessoas;
}
