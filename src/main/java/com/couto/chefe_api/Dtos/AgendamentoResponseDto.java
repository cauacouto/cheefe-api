package com.couto.chefe_api.Dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgendamentoResponseDto {

    private Long id;
    private LocalDateTime dataHora;
    private String status;

    private String nomeUsuario;


    private String rua;
    private String numero;
    private String bairro;
    private String cidade;
    private String tipoDeResidencia;
    private String complemento;
    private String estado;
    private String cep;

    private List<String> chefes;



}
