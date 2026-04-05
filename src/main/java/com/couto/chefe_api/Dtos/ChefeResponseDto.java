package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;


public record ChefeResponseDto(
    String nome,
    String nacionalidade,
    Especialidades especialidade,
    int anosDeExperiencia,
    Senioridade senioridade,
    boolean disponivel,
    String idiomas


) {


    public ChefeResponseDto(ChefeModel model) {
        this(model.getNome(), model.getNacionalidade(), model.getEspecialidade(), model.getAnosDeExperiencia(), model.getSenioridade(), model.isDisponivel(), model.getIdiomas());
    }
}
