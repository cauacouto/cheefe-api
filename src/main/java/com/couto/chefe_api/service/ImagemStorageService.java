package com.couto.chefe_api.service;

import com.couto.chefe_api.enums.PastaImagem;
import org.springframework.web.multipart.MultipartFile;

public interface ImagemStorageService {

    /**
     * Valida o arquivo (presença, tipo, conteúdo e tamanho) e só então envia.
     *
     * @return a URL pública (https) da imagem
     * @throws com.couto.chefe_api.exception.ImagemInvalidaException se o arquivo for inválido (400)
     * @throws com.couto.chefe_api.exception.FalhaUploadImagemException se o armazenamento falhar (502)
     */
    String enviar(MultipartFile arquivo, PastaImagem pasta);
}
