package com.couto.chefe_api.exception;

import org.springframework.http.HttpStatus;

/**
 * O provedor falhou, não respondeu a tempo ou não existe ainda (503). Sem causa de propósito: a resposta do provedor (que pode citar
 * o número) não chega ao cliente nem ao log de erro; quem lança já registrou o status, sem dados pessoais.
 */
public class SmsIndisponivelException extends NegocioException {

    public static final String AO_ENVIAR = "Não foi possível enviar o SMS agora";
    public static final String AO_CONFERIR = "Não foi possível conferir o código agora";

    public SmsIndisponivelException(String mensagem) {
        super(mensagem, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
