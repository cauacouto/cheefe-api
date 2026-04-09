package com.couto.chefe_api.enums;

import com.couto.chefe_api.domin.EnderecoModel;

public enum TipoDeResidencia {
    CASA {
        public void validar(EnderecoModel endereco) {
            // casa não exige nada
        }
    },
    APARTAMENTO {
        public void validar(EnderecoModel endereco) {
            if (endereco.getComplemento() == null || endereco.getComplemento().isBlank()) {
                throw new RuntimeException("Apartamento exige complemento (bloco/número do apto/nomeCondomino)");
            }
        }
    };

    public abstract void validar(EnderecoModel endereco);
}

