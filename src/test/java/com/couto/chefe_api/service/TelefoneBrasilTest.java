package com.couto.chefe_api.service;

import com.couto.chefe_api.exception.TelefoneInvalidoException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelefoneBrasilTest {

    @ParameterizedTest
    @ValueSource(strings = {"+5511987654321", "+55 (11) 98765-4321", "11987654321", "(11) 98765-4321",
            "11 9 8765 4321", "5511987654321", " +55 11 98765-4321 "})
    void aceitaCelularComOuSemPaisESeparadores(String entrada) {
        assertThat(TelefoneBrasil.normalizar(entrada)).isEqualTo("+5511987654321");
    }

    @Test
    void dddCincoCincoNaoEhConfundidoComOPais() {
        assertThat(TelefoneBrasil.normalizar("55987654321")).isEqualTo("+5555987654321");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1187654321", "11887654321", "119876543210", "01987654321", "10987654321",
            "+1 11987654321", "+54 11987654321", "11.98765.4321", "11 98765-432a", "+55+5511987654321",
            "551187654321", "   "})
    void foraDoFormatoEhInvalido(String entrada) {
        assertThatThrownBy(() -> TelefoneBrasil.normalizar(entrada))
                .isInstanceOf(TelefoneInvalidoException.class)
                .hasMessage("Telefone inválido");
    }

    @Test
    void mascaraMostraSoOsUltimosQuatroDigitos() {
        assertThat(TelefoneBrasil.mascarar("+5511987654321")).isEqualTo("+55*******4321").doesNotContain("98765");
    }
}
