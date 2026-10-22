package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Usado no PATCH de chefe: campos nulos são ignorados pelo mapper, então as
 * regras só valem para o que vier preenchido (texto não pode vir em branco).
 */
public record ChefeRequestDto(
        @Pattern(regexp = ".*\\S.*", message = "não pode ficar em branco")
        @Size(max = 100)
        String nome,

        @Pattern(regexp = ".*\\S.*", message = "não pode ficar em branco")
        @Size(max = 100)
        String nacionalidade,

        Especialidades especialidade,

        @PositiveOrZero
        Integer anosDeExperiencia,

        Senioridade senioridade,

        @Pattern(regexp = ".*\\S.*", message = "não pode ficar em branco")
        @Size(max = 200)
        String idiomas,

        @Positive
        @Digits(integer = 8, fraction = 2)
        BigDecimal valorHora
) {
}
