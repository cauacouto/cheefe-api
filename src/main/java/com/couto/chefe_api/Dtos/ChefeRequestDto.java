package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChefeRequestDto(
        @NotBlank
    String nome,
        @NotBlank
    String nacionalidade,

    Especialidades especialidade,

        @NotNull
    int anosDeExperiencia,

    Senioridade senioridade,
        @NotBlank
    String idiomas


) {
}

