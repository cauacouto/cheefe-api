package com.couto.chefe_api.Dtos;

import jakarta.validation.constraints.NotBlank;

public record CadastraPratosDtoRequest(
        @NotBlank
        String nomePrato,
        @NotBlank
        String descricao,
         String imageUrl



) {
}
