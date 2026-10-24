package com.couto.chefe_api;

import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.UserModel;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dados de teste com telefone verificado: sem ele o chef some da busca e ninguém agenda, conversa ou avalia.
 * Os números são únicos na JVM (o banco tem um telefone por conta) e todos do DDD 11.
 */
public final class TelefoneDeTeste {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    private TelefoneDeTeste() {
    }

    /** Celular válido em E.164 que nenhuma outra conta dos testes usa. */
    public static String novoNumero() {
        return String.format("+55119%08d", SEQUENCIA.incrementAndGet());
    }

    public static ChefeModel verificado(ChefeModel chefe) {
        chefe.setTelefoneVerificado(novoNumero());
        chefe.setTelefoneVerificadoEm(LocalDateTime.now());
        return chefe;
    }

    public static UserModel verificado(UserModel cliente) {
        cliente.setTelefoneVerificado(novoNumero());
        cliente.setTelefoneVerificadoEm(LocalDateTime.now());
        return cliente;
    }
}
