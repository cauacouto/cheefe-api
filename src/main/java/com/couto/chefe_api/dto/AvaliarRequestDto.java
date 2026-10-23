package com.couto.chefe_api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Avaliação de um agendamento concluído. {@code chefeId} só vale para o cliente (qual chef ele avalia);
 * o chef sempre avalia o cliente. {@code anonimo} nulo = anônimo (padrão).
 */
public record AvaliarRequestDto(
        UUID chefeId,
        @NotNull @Min(1) @Max(5) Integer nota,
        @Size(max = 5) List<String> tags,
        @Size(max = 1000) String comentario,
        Boolean anonimo
) {
}
