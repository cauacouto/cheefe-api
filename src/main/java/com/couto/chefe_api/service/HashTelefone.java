package com.couto.chefe_api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256(pepper + telefone E.164) em hex: é o que fica de um telefone banido (ver TelefoneBanido). O pepper
 * (CHEFHOME_PEPPER_TELEFONE) fica fora do banco: sem ele, um vazamento da tabela não vira lista de números
 * (os celulares do Brasil são poucos o bastante para testar todos contra um hash sem segredo).
 */
public class HashTelefone {

    private final String pepper;

    public HashTelefone(String pepper) {
        this.pepper = pepper;
    }

    public String de(String e164) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest((pepper + e164).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
