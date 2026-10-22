package com.couto.chefe_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnviarMensagemDto(@NotBlank @Size(max = 2000) String texto) {
}
