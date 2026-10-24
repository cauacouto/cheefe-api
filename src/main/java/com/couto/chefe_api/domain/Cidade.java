package com.couto.chefe_api.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Comparação de cidades escritas de jeitos diferentes ("São Paulo", "sao  paulo", "SÃO PAULO"): sem acento,
 * em minúsculas e com espaços únicos. Vale para a cidade do chef e a do endereço do cliente.
 */
public final class Cidade {

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern ESPACOS = Pattern.compile("\\s+");

    private Cidade() {
    }

    /** Nulo para nulo ou em branco. */
    public static String normalizar(String cidade) {
        if (cidade == null || cidade.isBlank()) {
            return null;
        }
        String semAcento = MARCAS.matcher(Normalizer.normalize(cidade.strip(), Normalizer.Form.NFD)).replaceAll("");
        return ESPACOS.matcher(semAcento).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    /** UF em maiúsculas; nulo para nulo ou em branco. */
    public static String uf(String estado) {
        return estado == null || estado.isBlank() ? null : estado.strip().toUpperCase(Locale.ROOT);
    }

    /** Mesma cidade e mesma UF (há cidades com o mesmo nome em estados diferentes). */
    public static boolean mesma(String cidadeA, String ufA, String cidadeB, String ufB) {
        String a = normalizar(cidadeA);
        String uA = uf(ufA);
        return a != null && uA != null && a.equals(normalizar(cidadeB)) && uA.equals(uf(ufB));
    }
}
