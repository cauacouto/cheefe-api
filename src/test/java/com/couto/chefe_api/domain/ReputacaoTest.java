package com.couto.chefe_api.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReputacaoTest {

    @Test
    void metadeArredondaParaCima() {
        // 89/20 = 4,45 exato: HALF_UP dá 4,5 (HALF_EVEN daria 4,4)
        assertThat(Reputacao.media(89, 20)).isEqualByComparingTo(new BigDecimal("4.5"));
        // 111/25 = 4,44: abaixo da metade
        assertThat(Reputacao.media(111, 25)).isEqualByComparingTo(new BigDecimal("4.4"));
        // 4,46 sobe
        assertThat(Reputacao.media(223, 50)).isEqualByComparingTo(new BigDecimal("4.5"));
    }

    @Test
    void abaixoDeCincoAvaliacoesNaoTemMedia() {
        assertThat(Reputacao.media(16, 4)).isNull();
        assertThat(Reputacao.media(0, 0)).isNull();
        assertThat(Reputacao.media(25, 5)).isEqualByComparingTo(new BigDecimal("5.0"));
        assertThat(Reputacao.media(5, 5)).isEqualByComparingTo(new BigDecimal("1.0"));
    }

    @Test
    void mediaTemUmaCasaDecimal() {
        assertThat(Reputacao.media(24, 5).toPlainString()).isEqualTo("4.8");
        assertThat(Reputacao.media(20, 5).toPlainString()).isEqualTo("4.0");
    }
}
