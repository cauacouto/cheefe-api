package com.couto.chefe_api.repository;

import java.time.LocalDateTime;
import java.util.UUID;

/** Uma linha da agregação de "meus clientes", com o nome inteiro; o DTO guarda só o primeiro. */
public record ClienteDoChefResumo(UUID id, String nome, String imageUrl, long total,
                                  LocalDateTime ultimo, LocalDateTime proximo) {
}
