package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoUsuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequestDto {



    @NotNull
    private TipoUsuario tipoUsuario;

// campos usuario
    @NotBlank
    private String nome;

    private List<@Valid EnderecoRequestDto> endereco;


    // campos do chef: opcionais no onboarding, mas com as mesmas regras do PATCH /chefe/me quando vierem
    @Pattern(regexp = ".*\\S.*", message = "não pode ficar em branco")
    @Size(max = 100)
    private String nacionalidade;
    private Especialidades especialidade;
    private Senioridade senioridade;
    @PositiveOrZero
    private Integer anosDeExperiencia;
    @Pattern(regexp = ".*\\S.*", message = "não pode ficar em branco")
    @Size(max = 200)
    private String idiomas;
    @Positive
    @Digits(integer = 8, fraction = 2)
    private BigDecimal valorHora;
}
