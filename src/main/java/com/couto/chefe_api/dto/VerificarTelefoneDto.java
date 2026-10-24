package com.couto.chefe_api.dto;

/** Telefone (mesmo formato do pedido) e o código que chegou por SMS. */
public record VerificarTelefoneDto(String telefone, String codigo) {
}
