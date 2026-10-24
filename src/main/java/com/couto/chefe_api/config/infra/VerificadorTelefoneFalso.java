package com.couto.chefe_api.config.infra;

import com.couto.chefe_api.service.TelefoneBrasil;
import com.couto.chefe_api.service.VerificadorTelefone;
import lombok.extern.slf4j.Slf4j;

/**
 * Desenvolvimento e testes, com CHEFHOME_TELEFONE_FALSO=true (nunca em prod, ver TelefoneConfig): não manda nada e o
 * código é sempre {@value #CODIGO}.
 */
@Slf4j
public class VerificadorTelefoneFalso implements VerificadorTelefone {

    public static final String CODIGO = "000000";

    @Override
    public void enviarCodigo(String e164) {
        log.info("código de verificação para {}: {}", TelefoneBrasil.mascarar(e164), CODIGO);
    }

    @Override
    public boolean conferir(String e164, String codigo) {
        return CODIGO.equals(codigo);
    }
}
