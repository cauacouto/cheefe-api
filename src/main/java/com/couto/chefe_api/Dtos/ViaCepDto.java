package com.couto.chefe_api.Dtos;

public record ViaCepDto(
        String cep,
        String logradouro,
        String bairro,
        String localidade,
        String uf
) {
}
