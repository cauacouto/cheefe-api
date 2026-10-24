package com.couto.chefe_api.dto;

/** O formato (celular do Brasil) é conferido no serviço: fora dele, 400 "Telefone inválido". */
public record PedirCodigoTelefoneDto(String telefone) {
}
