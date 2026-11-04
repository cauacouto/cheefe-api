package com.couto.chefe_api.repository;

import java.time.LocalDateTime;
import java.util.UUID;

/** O que a rotina do aviso de um ano precisa de cada candidato para agrupar por (chef, cliente). */
public record CandidatoAoAvisoDeUmAno(Long id, UUID chefeId, UUID clienteId, LocalDateTime inicio) {
}
