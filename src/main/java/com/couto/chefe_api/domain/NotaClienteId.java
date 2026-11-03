package com.couto.chefe_api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

/** Uma nota por par: o chef e o cliente sobre quem ele escreve. */
@Embeddable
public record NotaClienteId(
        @Column(name = "chefe_id") UUID chefeId,
        @Column(name = "cliente_id") UUID clienteId
) {
}
