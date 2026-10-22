package com.couto.chefe_api.Dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnviarMensagemDto(@NotBlank @Size(max = 2000) String texto) {
}
