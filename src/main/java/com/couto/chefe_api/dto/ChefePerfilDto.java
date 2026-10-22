package com.couto.chefe_api.dto;

import com.couto.chefe_api.domain.ChefeModel;
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
        List<String> camposPendentes,
        boolean suspenso,
        boolean ocultoPorDenuncias
) {

    public static ChefePerfilDto de(ChefeModel chefe, boolean ocultoPorDenuncias) {
        return new ChefePerfilDto(chefe.getId(), chefe.getNome(), chefe.getNacionalidade(), chefe.getEspecialidade(),
                chefe.getAnosDeExperiencia(), chefe.getSenioridade(), chefe.isDisponivel(), chefe.getIdiomas(),
                chefe.getValorHora(), chefe.getImageUrl(), chefe.isPerfilCompleto(), chefe.camposPendentes(),
                chefe.isSuspenso(), ocultoPorDenuncias);
    }
}
