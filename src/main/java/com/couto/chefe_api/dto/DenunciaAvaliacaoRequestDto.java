package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.MotivoDenunciaAvaliacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DenunciaAvaliacaoRequestDto(
        @NotNull MotivoDenunciaAvaliacao motivo,
        @Size(max = 1000) String descricao
) {
}
