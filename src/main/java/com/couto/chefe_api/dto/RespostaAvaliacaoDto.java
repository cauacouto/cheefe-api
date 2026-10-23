package com.couto.chefe_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RespostaAvaliacaoDto(@NotBlank @Size(max = 1000) String texto) {
}
