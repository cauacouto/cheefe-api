package com.couto.chefe_api.Services;

public record ImagemValidada(
        byte[] conteudo,
        String contentType,
        String extensao
) {
}
