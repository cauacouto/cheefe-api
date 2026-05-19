package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;

import java.math.BigDecimal;
import java.util.UUID;


public record ChefeResponseDto(
        UUID id,
    String nome,
    String nacionalidade,
    Especialidades especialidade,
    int anosDeExperiencia,
    Senioridade senioridade,
    boolean disponivel,
    String idiomas,
        BigDecimal valorHora


) {


    public ChefeResponseDto(ChefeModel model) {
        this(model.getId(), model.getNome(), model.getNacionalidade(), model.getEspecialidade(), model.getAnosDeExperiencia(), model.getSenioridade(), model.isDisponivel(), model.getIdiomas(),model.getValorHora());
    }
}
