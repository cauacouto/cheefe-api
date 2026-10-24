package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.BloqueioConta;
import com.couto.chefe_api.enums.StatusBloqueio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface BloqueioContaRepository extends JpaRepository<BloqueioConta, Long> {

    Page<BloqueioConta> findByStatusOrderByCriadoEmAscIdAsc(StatusBloqueio status, Pageable pageable);

    /** SELECT ... FOR UPDATE: moderadores simultâneos na mesma decisão esperam e o segundo recebe 409. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BloqueioConta b WHERE b.id = :id")
    Optional<BloqueioConta> travarPorId(@Param("id") Long id);

    /** Outro bloqueio da mesma conta que ainda a mantém suspensa (aberto ou confirmado). */
    boolean existsByContaIdAndIdNotAndStatusIn(UUID contaId, Long id, Collection<StatusBloqueio> status);

    /** Os bloqueios da conta, para apagar as notificações dos moderadores que apontam para eles. */
    @Query("SELECT b.id FROM BloqueioConta b WHERE b.contaId = :contaId")
    java.util.List<Long> idsDaConta(@Param("contaId") UUID contaId);

    void deleteByContaId(UUID contaId);
}
