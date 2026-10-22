package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.enums.Especialidades;

import java.util.UUID;

/** Chef dentro de outra resposta (ex.: agendamento): o suficiente para mostrar e abrir o perfil. */
public record ChefeResumoDto(UUID id, String nome, String imageUrl, Especialidades especialidade) {
}
