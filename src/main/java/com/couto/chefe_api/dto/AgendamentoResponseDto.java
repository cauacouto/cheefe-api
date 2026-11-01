package com.couto.chefe_api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgendamentoResponseDto {

    private Long id;
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime dataHoraInicial;

    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime dataHoraFinal;

    private String nomeUsuario;
    private Integer quantidadePessoas;
    private BigDecimal valorTotal;
    private String rua;
    private String numero;
    private String bairro;
    private String cidade;
    private String tipoDeResidencia;
    private String complemento;
    private String estado;
    private String cep;
    private ChefeResumoDto chefe;
    private String status;
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime criadoEm;




}
