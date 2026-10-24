package com.couto.chefe_api.config.infra;

import com.couto.chefe_api.exception.SmsIndisponivelException;
import com.couto.chefe_api.service.VerificadorTelefone;

/**
 * Sem provedor de verificação (parado até a escolha do canal, ex.: WhatsApp): pedir e conferir código respondem
 * 503. Nada é exigido enquanto isso, então nenhuma ação do app depende daqui.
 */
public class VerificadorTelefoneIndisponivel implements VerificadorTelefone {

    static final String MENSAGEM = "Verificação de telefone ainda não disponível";

    @Override
    public void enviarCodigo(String e164) {
        throw new SmsIndisponivelException(MENSAGEM);
    }

    @Override
    public boolean conferir(String e164, String codigo) {
        throw new SmsIndisponivelException(MENSAGEM);
    }
}
