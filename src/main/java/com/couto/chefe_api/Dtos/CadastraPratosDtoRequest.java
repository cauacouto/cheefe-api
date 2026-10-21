package com.couto.chefe_api.Dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sem {@code imageUrl}: a imagem do prato só entra pelo upload validado (parte "foto" do POST multipart).
 */
public record CadastraPratosDtoRequest(
        @NotBlank
        @Size(max = 100)
        String nomePrato,
        @NotBlank
        @Size(max = 1000)
        String descricao
) {
}
