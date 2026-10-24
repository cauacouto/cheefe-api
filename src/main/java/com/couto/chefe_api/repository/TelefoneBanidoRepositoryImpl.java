package com.couto.chefe_api.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;

import java.time.LocalDateTime;

class TelefoneBanidoRepositoryImpl implements TelefoneBanidoRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * INSERT ... ON CONFLICT DO NOTHING do Hibernate (vira ON CONFLICT no Postgres e MERGE no H2): sem
     * SELECT antes, duas confirmações simultâneas do mesmo telefone não estouram a chave primária, e a
     * API nunca precisa de UPDATE na tabela (V12 revoga).
     */
    @Override
    public void banirSeAusente(String hash, LocalDateTime banidoEm) {
        entityManager.unwrap(Session.class).createMutationQuery(
                        "insert into TelefoneBanido (hash, banidoEm) values (:hash, :banidoEm) on conflict do nothing")
                .setParameter("hash", hash)
                .setParameter("banidoEm", banidoEm)
                .executeUpdate();
    }
}
