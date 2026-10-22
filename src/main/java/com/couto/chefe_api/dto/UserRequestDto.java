package com.couto.chefe_api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UserRequestDto(@NotBlank @Size(max = 100) String nome,
                             @NotBlank @Size(max = 20) String telefone,
                             @NotNull @NotEmpty List<@Valid @NotNull EnderecoRequestDto> endereco
) {
}
