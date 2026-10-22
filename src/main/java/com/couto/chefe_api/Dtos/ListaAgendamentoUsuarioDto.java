package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.enums.Especialidades;
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
    /** Nome e especialidade do primeiro chef; mantidos por compatibilidade, use {@code chefes}. */
    private String nomesChefes;
    private Especialidades especialidades;
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime criadoEm;

    private List<ChefeResumoDto> chefes;
}
