package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.MotivoCancelamento;
import jakarta.validation.constraints.Size;

/** Corpo opcional do cancelamento pelo cliente: motivo pré-definido e/ou texto livre. */
public record CancelamentoRequestDto(MotivoCancelamento motivo, @Size(max = 300) String descricao) {
}
