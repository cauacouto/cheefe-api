package com.couto.chefe_api.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Regra única da média exibida: com poucas avaliações a média engana (e identificaria quem avaliou). */
public final class Reputacao {

    /** Abaixo disso a média sai nula ("Novo"). */
    public static final int MINIMO_PARA_MEDIA = 5;

    private Reputacao() {
    }

    /** {@code soma/total} com uma casa (HALF_UP), ou nulo com menos de {@link #MINIMO_PARA_MEDIA} avaliações. */
    public static BigDecimal media(int soma, int total) {
        if (total < MINIMO_PARA_MEDIA) {
            return null;
        }
        return BigDecimal.valueOf(soma).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
