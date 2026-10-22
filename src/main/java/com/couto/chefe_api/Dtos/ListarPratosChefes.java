package com.couto.chefe_api.Dtos;

import java.util.UUID;

public record ListarPratosChefes(
        Integer id,
        UUID chefeId,
        String nomeChefe,
        String nomePrato,
        String descricao,
        String imageUrl
) {
}
