package com.couto.chefe_api.repository;

import java.time.LocalDate;

public interface TelefoneBanidoRepositoryCustom {

    /** Grava o hash se ainda não existe; o mesmo hash de novo (ou em paralelo) não é erro nem altera a linha. */
    void banirSeAusente(String hash, LocalDate banidoEm);
}
