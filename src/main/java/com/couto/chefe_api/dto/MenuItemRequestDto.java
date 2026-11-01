package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.EtapaMenu;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Prato do menu em uma etapa. Que o prato é do próprio chef e não se repete é validado no serviço. */
public record MenuItemRequestDto(
        @NotNull Integer pratoId,
        @NotNull EtapaMenu etapa,
        @NotNull @PositiveOrZero Integer ordem
) {
}
