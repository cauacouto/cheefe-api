package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.MotivoRecusa;
import jakarta.validation.constraints.Size;

/** Corpo opcional da recusa ou desistência do chef: motivo pré-definido e/ou texto livre. */
public record RecusaRequestDto(MotivoRecusa motivo, @Size(max = 300) String descricao) {
}
