package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.domin.Pratos;

public record CadastratPratosDtoResponse(
        Integer id,
        String nome,
        String descricao,
        String imageUrl






) {
    public CadastratPratosDtoResponse(Pratos pratos) {

        this(
                pratos.getId(),
                pratos.getNome(),
                pratos.getDescricao(),
                pratos.getImageUrl()
        );
    }
}
