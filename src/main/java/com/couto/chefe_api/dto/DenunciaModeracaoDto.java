package com.couto.chefe_api.dto;

import com.couto.chefe_api.domain.Denuncia;
import com.couto.chefe_api.enums.MotivoDenuncia;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

public record DenunciaModeracaoDto(
        Long id,
        MotivoDenuncia motivo,
        String descricao,
        StatusDenuncia status,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
        LocalDateTime criadaEm,
        Pessoa chefe,
        Pessoa cliente,
        Long agendamentoId
) {

    public record Pessoa(UUID id, String nome) {
    }

    public static DenunciaModeracaoDto de(Denuncia denuncia) {
        return new DenunciaModeracaoDto(denuncia.getId(), denuncia.getMotivo(), denuncia.getDescricao(),
                denuncia.getStatus(), denuncia.getCriadaEm(),
                new Pessoa(denuncia.getChefe().getId(), denuncia.getChefe().getNome()),
                new Pessoa(denuncia.getCliente().getId(), denuncia.getCliente().getNome()),
                denuncia.getAgendamento().getId());
    }
}
