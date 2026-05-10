package com.couto.chefe_api.Dtos;

public record ListarPratosChefes(
        String nomeChefe,
        String nomePrato,
        String descricao,
        String imagemUrl
) {
}
