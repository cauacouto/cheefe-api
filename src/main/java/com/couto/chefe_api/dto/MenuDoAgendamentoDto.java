package com.couto.chefe_api.dto;

import java.math.BigDecimal;

/** O menu como estava no pedido (a cópia gravada no agendamento): editar o menu depois não muda este retrato. */
public record MenuDoAgendamentoDto(
        Long id,
        String nome,
        BigDecimal precoPorPessoa,
        Integer duracaoMinutos,
        Boolean ingredientesInclusos
) {
}
