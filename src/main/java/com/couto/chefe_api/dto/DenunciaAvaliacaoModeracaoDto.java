package com.couto.chefe_api.dto;

import com.couto.chefe_api.domain.DenunciaAvaliacao;
import com.couto.chefe_api.enums.MotivoDenunciaAvaliacao;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Só para a moderação: aqui o autor aparece mesmo em avaliação anônima. {@code textoDenunciado} é o
 * exibido no momento da denúncia; {@code comentarioOriginal}, o que o cliente escreveu antes do filtro.
 */
public record DenunciaAvaliacaoModeracaoDto(
        Long id,
        MotivoDenunciaAvaliacao motivo,
        String descricao,
        StatusDenuncia status,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
        LocalDateTime criadaEm,
        String textoDenunciado,
        String comentarioOriginal,
        Long avaliacaoId,
        int nota,
        boolean comentarioRemovido,
        Pessoa denunciante,
        Pessoa autor
) {

    public record Pessoa(UUID id, String nome, TipoUsuario tipo) {

        public static Pessoa de(UUID id, TipoUsuario tipo, Map<UUID, String> nomes) {
            return new Pessoa(id, nomes.get(id), tipo);
        }
    }

    public static DenunciaAvaliacaoModeracaoDto de(DenunciaAvaliacao denuncia, Map<UUID, String> nomes) {
        var avaliacao = denuncia.getAvaliacao();
        return new DenunciaAvaliacaoModeracaoDto(denuncia.getId(), denuncia.getMotivo(), denuncia.getDescricao(),
                denuncia.getStatus(), denuncia.getCriadaEm(), denuncia.getTextoDenunciado(),
                avaliacao.getComentarioOriginal(), avaliacao.getId(), avaliacao.getNota(), avaliacao.isComentarioRemovido(),
                Pessoa.de(denuncia.getDenuncianteId(), denuncia.getDenuncianteTipo(), nomes),
                Pessoa.de(denuncia.getAutorId(), denuncia.getAutorTipo(), nomes));
    }
}
