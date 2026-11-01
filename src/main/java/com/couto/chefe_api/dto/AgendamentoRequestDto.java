package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.OcasiaoMenu;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pedido de um menu. O chef, o fim e o valor vêm do menu; as regras que dependem dele (limite de convidados,
 * restrições até o total de pessoas) são validadas no serviço.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgendamentoRequestDto {

    @NotNull
    private Long menuId;

    @NotNull
    @Future
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime dataHoraInicial;

    @NotNull
    private Long enderecoId;

    @NotNull
    @Positive
    private Integer quantidadePessoas;

    @NotNull
    private OcasiaoMenu ocasiao;

    private List<@NotNull @Valid RestricaoDto> restricoes;

    @Size(max = 1000)
    private String observacoes;
}
