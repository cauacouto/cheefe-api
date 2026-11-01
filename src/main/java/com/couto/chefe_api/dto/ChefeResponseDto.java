package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;


public record ChefeResponseDto(
        UUID id,
    String nome,
    String nacionalidade,
    Set<Especialidades> especialidades,
    Integer anosDeExperiencia,
    Senioridade senioridade,
    boolean disponivel,
    String idiomas,
        /** Menor preço por pessoa entre os menus ativos; preenchido pelo serviço. */
        BigDecimal precoAPartirDe,
        String imageUrl,
        /** Nula com menos de 5 avaliações ("Novo"). */
        BigDecimal notaMedia,
        int totalAvaliacoes,
        /** Cidade de origem: o chef só atende nela. */
        String cidade,
        String estado


) {

    /** Estático para o MapStruct não tomar por um setter. */
    public static ChefeResponseDto comPrecoAPartirDe(ChefeResponseDto c, BigDecimal preco) {
        return new ChefeResponseDto(c.id, c.nome, c.nacionalidade, c.especialidades, c.anosDeExperiencia, c.senioridade,
                c.disponivel, c.idiomas, preco, c.imageUrl, c.notaMedia, c.totalAvaliacoes, c.cidade, c.estado);
    }
}
