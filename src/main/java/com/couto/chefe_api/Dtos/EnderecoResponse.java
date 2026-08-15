package com.couto.chefe_api.Dtos;

public record EnderecoResponse(
        String cep,
        String rua,
        String bairro,
        String cidade,
        String estado
) {
}
