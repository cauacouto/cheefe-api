package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.domin.AcaoModeracao;
import com.couto.chefe_api.enums.TipoAcaoModeracao;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

public record AcaoModeracaoDto(
        Long id,
        String moderadorEmail,
        TipoAcaoModeracao acao,
        UUID chefeId,
        Long denunciaId,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
        LocalDateTime realizadaEm
) {

    public static AcaoModeracaoDto de(AcaoModeracao acao) {
        return new AcaoModeracaoDto(acao.getId(), acao.getModeradorEmail(), acao.getAcao(),
                acao.getChefeId(), acao.getDenunciaId(), acao.getRealizadaEm());
    }
}
