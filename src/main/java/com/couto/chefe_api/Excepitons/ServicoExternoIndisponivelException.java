package com.couto.chefe_api.Excepitons;

import org.springframework.http.HttpStatus;

/**
 * Um serviço externo de consulta (ex.: ViaCep) falhou ou não respondeu a tempo (502).
 */
public class ServicoExternoIndisponivelException extends NegocioException {

    public ServicoExternoIndisponivelException(String servico, Throwable cause) {
        super("Serviço externo indisponível: " + servico, HttpStatus.BAD_GATEWAY, cause);
    }
}
