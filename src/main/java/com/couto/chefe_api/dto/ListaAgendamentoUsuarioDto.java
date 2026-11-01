package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.OcasiaoMenu;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
@Getter
@Setter
public class ListaAgendamentoUsuarioDto {

    private Long id;
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime dataHora;
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime dataHoraFinal;
    private Integer quantidadePessoas;
    private BigDecimal valorTotal;
    private StatusAgendamento status;
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime criadoEm;

    private ChefeResumoDto chefe;
    private MenuDoAgendamentoDto menu;
    private OcasiaoMenu ocasiao;
    private List<RestricaoDto> restricoes;
    private String observacoes;
}
