package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.enums.MotivoDenuncia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DenunciaRequestDto(
        @NotNull Long agendamentoId,
        @NotNull UUID chefeId,
        @NotNull MotivoDenuncia motivo,
        @NotBlank @Size(max = 1000) String descricao
) {
}
