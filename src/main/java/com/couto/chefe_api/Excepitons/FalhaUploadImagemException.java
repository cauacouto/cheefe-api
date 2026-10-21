package com.couto.chefe_api.Excepitons;

import org.springframework.http.HttpStatus;

/**
 * O serviço externo de armazenamento de imagens falhou (502).
 */
public class FalhaUploadImagemException extends NegocioException {

    public FalhaUploadImagemException(Throwable cause) {
        super("Erro ao enviar imagem para o armazenamento", HttpStatus.BAD_GATEWAY, cause);
    }
}
