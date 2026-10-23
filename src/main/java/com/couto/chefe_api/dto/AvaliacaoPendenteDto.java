package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.TipoUsuario;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

/** Quem ainda pode ser avaliado neste agendamento, e até quando. */
public record AvaliacaoPendenteDto(UUID id, String nome, TipoUsuario tipo,
                                   @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime prazo) {
}
