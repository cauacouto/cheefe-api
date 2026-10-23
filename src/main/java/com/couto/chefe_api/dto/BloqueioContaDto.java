package com.couto.chefe_api.dto;

import com.couto.chefe_api.domain.BloqueioConta;
import com.couto.chefe_api.enums.CategoriaConteudo;
import com.couto.chefe_api.enums.StatusBloqueio;
import com.couto.chefe_api.enums.TipoUsuario;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

/** Item da fila de bloqueios automáticos; o texto é o que disparou o bloqueio. */
public record BloqueioContaDto(
        Long id,
        UUID contaId,
        TipoUsuario contaTipo,
        String nome,
        CategoriaConteudo categoria,
        String termo,
        String texto,
        StatusBloqueio status,
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
        LocalDateTime criadoEm
) {

    public static BloqueioContaDto de(BloqueioConta bloqueio, String nome) {
        return new BloqueioContaDto(bloqueio.getId(), bloqueio.getContaId(), bloqueio.getContaTipo(), nome,
                bloqueio.getCategoria(), bloqueio.getTermo(), bloqueio.getTexto(), bloqueio.getStatus(),
                bloqueio.getCriadoEm());
    }
}
