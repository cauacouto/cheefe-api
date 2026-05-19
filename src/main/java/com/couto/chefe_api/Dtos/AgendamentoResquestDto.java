package com.couto.chefe_api.Dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgendamentoResquestDto {

     @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime dataHoraInicial;

    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime dataHoraFinal;


     private Long enderecoId;

    private List<UUID> chefeIds;

    private Integer quantidadePessoas;
}
