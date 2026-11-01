package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

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

        /** De 1 a 3, sem repetir (a repetição é recusada no serviço). */
        @Size(min = 1, max = 3)
        List<Especialidades> especialidades,

        @PositiveOrZero
        Integer anosDeExperiencia,

        Senioridade senioridade,

        @Pattern(regexp = ".*\\S.*", message = "não pode ficar em branco")
        @Size(max = 200)
        String idiomas,

        /** Cidade de origem: o chef só atende nela. */
        @Pattern(regexp = ".*\\S.*", message = "não pode ficar em branco")
        @Size(max = 100)
        String cidade,

        @Pattern(regexp = "[A-Za-z]{2}", message = "deve ser a sigla da UF")
        String estado
) {
}
