package com.couto.chefe_api.dto;

public record EnderecoResponse(
        String cep,
        String rua,
        String bairro,
        String cidade,
        String estado
) {
}
