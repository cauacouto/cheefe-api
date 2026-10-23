package com.couto.chefe_api.dto;

import com.couto.chefe_api.domain.Avaliacao;
import com.couto.chefe_api.dto.DenunciaAvaliacaoModeracaoDto.Pessoa;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Comentário de um chef sobre um cliente: nunca é público, só a moderação lê. */
public record ComentarioClienteModeracaoDto(
        Long avaliacaoId,
        int nota,
        String comentario,
        String comentarioOriginal,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
        LocalDateTime criadaEm,
        Pessoa autor,
        Pessoa avaliado
) {

    public static ComentarioClienteModeracaoDto de(Avaliacao avaliacao, Map<UUID, String> nomes) {
        return new ComentarioClienteModeracaoDto(avaliacao.getId(), avaliacao.getNota(), avaliacao.getComentario(),
                avaliacao.getComentarioOriginal(), avaliacao.getCriadaEm(),
                Pessoa.de(avaliacao.getAutorId(), avaliacao.getAutorTipo(), nomes),
                Pessoa.de(avaliacao.getAvaliadoId(), avaliacao.getAvaliadoTipo(), nomes));
    }
}
