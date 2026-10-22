package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.domin.Conversa;
import com.couto.chefe_api.domin.DenunciaMensagem;
import com.couto.chefe_api.enums.MotivoDenunciaMensagem;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

/** O texto é o do momento da denúncia: editar ou apagar a mensagem depois não muda o que a moderação vê. */
public record DenunciaMensagemModeracaoDto(
        Long id,
        MotivoDenunciaMensagem motivo,
        String descricao,
        StatusDenuncia status,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
        LocalDateTime criadaEm,
        String textoDenunciado,
        Long mensagemId,
        Long conversaId,
        boolean mensagemApagada,
        Pessoa denunciante,
        Pessoa autor
) {

    public record Pessoa(UUID id, String nome, TipoUsuario tipo) {
    }

    public static DenunciaMensagemModeracaoDto de(DenunciaMensagem denuncia) {
        Conversa conversa = denuncia.getMensagem().getConversa();
        return new DenunciaMensagemModeracaoDto(denuncia.getId(), denuncia.getMotivo(), denuncia.getDescricao(),
                denuncia.getStatus(), denuncia.getCriadaEm(), denuncia.getTextoDenunciado(),
                denuncia.getMensagem().getId(), conversa.getId(), denuncia.getMensagem().isApagada(),
                pessoa(conversa, denuncia.getDenuncianteId(), denuncia.getDenuncianteTipo()),
                pessoa(conversa, denuncia.getAutorId(), denuncia.getAutorTipo()));
    }

    private static Pessoa pessoa(Conversa conversa, UUID id, TipoUsuario tipo) {
        String nome = tipo == TipoUsuario.CLIENTE ? conversa.getCliente().getNome() : conversa.getChefe().getNome();
        return new Pessoa(id, nome, tipo);
    }
}
