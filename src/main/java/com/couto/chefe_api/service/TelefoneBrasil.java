package com.couto.chefe_api.service;

import com.couto.chefe_api.exception.TelefoneInvalidoException;

import java.util.regex.Pattern;

/** Só celular do Brasil, em E.164 (+55 + DDD + 9 dígitos começando por 9). */
public final class TelefoneBrasil {

    private static final Pattern SEPARADORES = Pattern.compile("[\\s()\\-]");
    // DDD de 11 a 99 sem zero (não existe DDD terminado em 0) e celular de 9 dígitos começando por 9
    private static final Pattern CELULAR = Pattern.compile("[1-9][1-9]9\\d{8}");
    private static final String PAIS = "55";

    private TelefoneBrasil() {
    }

    /** Aceita com ou sem +55, espaços, parênteses e hífen; qualquer outra coisa → 400 "Telefone inválido". */
    public static String normalizar(String entrada) {
        if (entrada == null) {
            throw new TelefoneInvalidoException();
        }
        String digitos = SEPARADORES.matcher(entrada).replaceAll("");
        if (digitos.startsWith("+")) {
            if (!digitos.startsWith("+" + PAIS)) {
                throw new TelefoneInvalidoException();
            }
            digitos = digitos.substring(1 + PAIS.length());
        } else if (digitos.length() == 13 && digitos.startsWith(PAIS)) {
            // 13 dígitos só fecham com o país na frente (o DDD 55 sozinho daria 11)
            digitos = digitos.substring(PAIS.length());
        }
        if (!CELULAR.matcher(digitos).matches()) {
            throw new TelefoneInvalidoException();
        }
        return "+" + PAIS + digitos;
    }

    /** Para log: só o país e os 4 últimos dígitos ("+55*******4321"). */
    public static String mascarar(String e164) {
        if (e164 == null || e164.length() <= 7) {
            return "+55***";
        }
        return e164.substring(0, 3) + "*".repeat(e164.length() - 7) + e164.substring(e164.length() - 4);
    }
}
