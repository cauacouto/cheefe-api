package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.domin.Pratos;

public record CadastratPratosDtoResponse(
        Integer id,
        String nomePrato,
        String descricao,
        String imageUrl,
        String nomeChefe






) {

    public CadastratPratosDtoResponse(Pratos pratos) {
        this(
                pratos.getId(),
                pratos.getNomePrato(),
                pratos.getDescricao(),
                pratos.getImageUrl(),
                pratos.getChefeModel().getNome()
        );
    }
}
