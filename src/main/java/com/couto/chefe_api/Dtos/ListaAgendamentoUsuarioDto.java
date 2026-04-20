package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
@Getter
@Setter
public class ListaAgendamentoUsuarioDto {

    private LocalDateTime dataHora;
    private Integer quantidadePessoas;
    private StatusAgendamento status;
    private String nomesChefes;
    private Especialidades especialidades;
    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime criadoEm;

}
