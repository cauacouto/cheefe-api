package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.MotivoDenunciaMensagem;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DenunciaMensagemRequestDto(
        @NotNull MotivoDenunciaMensagem motivo,
        @Size(max = 1000) String descricao
) {
}
