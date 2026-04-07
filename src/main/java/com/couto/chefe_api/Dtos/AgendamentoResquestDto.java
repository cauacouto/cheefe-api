package com.couto.chefe_api.Dtos;

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


    private LocalDateTime dataHora;

    private Long enderecoId;

    private List<UUID> chefeIds;
}
