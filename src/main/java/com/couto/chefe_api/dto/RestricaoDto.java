package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.TipoRestricao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Restrição alimentar do pedido (entrada e saída). O teto da quantidade é a de pessoas, validada no serviço. */
public record RestricaoDto(
        @NotNull TipoRestricao tipo,
        @NotNull @Positive Integer quantidade
) {
}
