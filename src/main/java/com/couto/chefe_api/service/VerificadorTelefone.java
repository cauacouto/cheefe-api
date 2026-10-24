package com.couto.chefe_api.service;

/**
 * Quem gera, envia e confere o código por SMS (Twilio Verify): a API não gera nem guarda código.
 * Implementações: {@code TwilioVerificadorTelefone} e, fora de produção sem chaves, {@code VerificadorTelefoneFalso}.
 */
public interface VerificadorTelefone {

    /** Pede o envio do SMS para o celular em E.164; falha do provedor → 503. */
    void enviarCodigo(String e164);

    /** true só se o provedor aprovou o código; código errado, expirado ou tentativas esgotadas → false. */
    boolean conferir(String e164, String codigo);
}
