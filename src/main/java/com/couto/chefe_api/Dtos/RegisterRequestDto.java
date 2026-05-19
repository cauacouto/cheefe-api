package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoUsuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotBlank
    private String email;
    @NotBlank
    private String password;
    private List<EnderecoRequestDto> endereco;


   // campos chefe
    private String nacionalidade;
    private Especialidades especialidade;
    private Senioridade senioridade;
    private Integer anosDeExperiencia;
    private String idiomas;
    private BigDecimal valorHora;
    private String imageUrl;
}
