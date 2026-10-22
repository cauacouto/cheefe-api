package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.StatusAgendamento;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Getter
@Setter
public class ListaAgendamentoChefeDto {

    private Long id;

    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime dataHora;
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime dataHoraFinal;
    private BigDecimal valorTotal;
    private StatusAgendamento status;
    private String nomeUsuario;
   private Integer quantidadePessoas;
    private String rua;
    private String numero;
    private String bairro;
    private String cidade;
    private String tipoDeResidencia;
    private String complemento;
    private String estado;
    private String cep;
}
