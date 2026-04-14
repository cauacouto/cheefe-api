package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.enums.TipoDeResidencia;

public record EnderecoResponseDto(
        Long id,
        String rua,
        String numero,
        String complemento,
        String cep,
        String cidade,
        String bairro,
        String estado,
        TipoDeResidencia tipoDeResidencia
) {}
