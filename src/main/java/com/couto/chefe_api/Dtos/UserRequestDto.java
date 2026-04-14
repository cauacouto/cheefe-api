package com.couto.chefe_api.Dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UserRequestDto(@NotBlank String nome,
                            @NotBlank String email,
                             @NotBlank String password,
                            @NotBlank String telefone,
                            @NotNull @NotEmpty List<EnderecoRequestDto> endereco
                             ) {
}
