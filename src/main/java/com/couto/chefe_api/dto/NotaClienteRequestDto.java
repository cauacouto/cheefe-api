package com.couto.chefe_api.dto;

import jakarta.validation.constraints.Size;

/** Texto nulo ou em branco apaga a nota. */
public record NotaClienteRequestDto(@Size(max = 2000) String texto) {
}
