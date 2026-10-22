package com.couto.chefe_api.enums;

import com.couto.chefe_api.exception.EnderecoInvalidoException;
import com.couto.chefe_api.domain.EnderecoModel;

public enum TipoDeResidencia {
    CASA {
        public void validar(EnderecoModel endereco) {
            // casa não exige nada
        }
    },
    APARTAMENTO {
        public void validar(EnderecoModel endereco) {
            if (endereco.getComplemento() == null || endereco.getComplemento().isBlank()) {
                throw new EnderecoInvalidoException("Apartamento exige complemento (bloco/número do apto/nomeCondomino)");
            }
        }
    };

    public abstract void validar(EnderecoModel endereco);
}

