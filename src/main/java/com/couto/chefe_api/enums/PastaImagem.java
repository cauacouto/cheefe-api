package com.couto.chefe_api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Pastas do armazenamento de imagens. {@code PERFIL} é para a foto de perfil,
 * que ainda é enviada pelo LoginService (hoje, por engano, para a pasta de pratos).
 */
@Getter
@RequiredArgsConstructor
public enum PastaImagem {
    PRATOS("chefe-api/pratos"),
    PERFIL("chefe-api/perfil");

    private final String caminho;
}
