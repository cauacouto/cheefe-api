package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Perfil do próprio chef, com o que falta preencher para aparecer na busca. */
public record ChefePerfilDto(
        UUID id,
        String nome,
        String nacionalidade,
        Especialidades especialidade,
        Integer anosDeExperiencia,
        Senioridade senioridade,
        boolean disponivel,
        String idiomas,
        BigDecimal valorHora,
        String imageUrl,
        boolean perfilCompleto,
        List<String> camposPendentes
) {

    public static ChefePerfilDto de(ChefeModel chefe) {
        return new ChefePerfilDto(chefe.getId(), chefe.getNome(), chefe.getNacionalidade(), chefe.getEspecialidade(),
                chefe.getAnosDeExperiencia(), chefe.getSenioridade(), chefe.isDisponivel(), chefe.getIdiomas(),
                chefe.getValorHora(), chefe.getImageUrl(), chefe.isPerfilCompleto(), chefe.camposPendentes());
    }
}
