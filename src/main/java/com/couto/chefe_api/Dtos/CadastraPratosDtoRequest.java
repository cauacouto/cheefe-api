package com.couto.chefe_api.Dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastraPratosDtoRequest(
        @NotBlank
        @Size(max = 100)
        String nomePrato,
        @NotBlank
        @Size(max = 1000)
        String descricao,
        String imageUrl
) {
}
