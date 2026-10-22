package com.couto.chefe_api.enums;

import com.couto.chefe_api.exception.EnderecoInvalidoException;
import com.couto.chefe_api.domain.EnderecoModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TipoDeResidenciaTest {

    @Test
    void apartamentoSemComplementoLancaEnderecoInvalido() {
        EnderecoModel endereco = new EnderecoModel();
        endereco.setComplemento("  ");

        assertThrows(EnderecoInvalidoException.class, () -> TipoDeResidencia.APARTAMENTO.validar(endereco));
    }

    @Test
    void casaNaoExigeComplemento() {
        assertDoesNotThrow(() -> TipoDeResidencia.CASA.validar(new EnderecoModel()));
    }
}
