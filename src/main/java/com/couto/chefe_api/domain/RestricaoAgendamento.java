package com.couto.chefe_api.domain;

import com.couto.chefe_api.enums.TipoRestricao;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Quantas pessoas do pedido têm a restrição; um tipo aparece uma vez por agendamento. */
@Embeddable
public record RestricaoAgendamento(
        @Enumerated(EnumType.STRING) @Column(name = "tipo", nullable = false, length = 32) TipoRestricao tipo,
        @Column(name = "quantidade", nullable = false) int quantidade
) {
}
