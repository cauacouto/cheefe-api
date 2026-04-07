package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.domin.ChefeModel;

import java.time.LocalDateTime;
import java.util.List;

public class AgendendamenResponseDto {

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

    private List<ChefeModel> chefes;



}
