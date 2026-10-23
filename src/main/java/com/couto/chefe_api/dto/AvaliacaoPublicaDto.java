package com.couto.chefe_api.dto;

import java.util.List;

/**
 * Avaliação na lista pública do chef. {@code id} é o da avaliação (para responder/denunciar);
 * nenhum campo aponta o autor ou o agendamento.
 */
public record AvaliacaoPublicaDto(Long id, int nota, List<Tag> tags, String comentario, String autor, String quando,
                                  String resposta) {

    public record Tag(String codigo, String rotulo) {
    }
}
