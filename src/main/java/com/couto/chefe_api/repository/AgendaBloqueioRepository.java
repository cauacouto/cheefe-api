package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.AgendaBloqueio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendaBloqueioRepository extends JpaRepository<AgendaBloqueio, Long> {

    Optional<AgendaBloqueio> findByIdAndChefe_Id(Long id, UUID chefeId);

    /** Os que ainda não terminaram, por início. */
    List<AgendaBloqueio> findByChefe_IdAndFimAfterOrderByInicioAscIdAsc(UUID chefeId, LocalDateTime agora);

    long countByChefe_IdAndFimAfter(UUID chefeId, LocalDateTime agora);

    /** Intervalos semiabertos: encostar (fim de um = início do outro) não sobrepõe. */
    @Query("SELECT COUNT(b) > 0 FROM AgendaBloqueio b WHERE b.chefe.id = :chefeId AND b.inicio < :fim AND b.fim > :inicio")
    boolean existeSobreposto(@Param("chefeId") UUID chefeId, @Param("inicio") LocalDateTime inicio,
                             @Param("fim") LocalDateTime fim);

    @Query("SELECT b FROM AgendaBloqueio b WHERE b.chefe.id = :chefeId AND b.inicio < :fim AND b.fim > :inicio")
    List<AgendaBloqueio> sobrepostos(@Param("chefeId") UUID chefeId, @Param("inicio") LocalDateTime inicio,
                                     @Param("fim") LocalDateTime fim);
}
