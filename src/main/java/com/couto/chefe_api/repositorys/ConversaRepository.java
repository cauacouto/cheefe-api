package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.Conversa;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversaRepository extends JpaRepository<Conversa, Long> {

    Optional<Conversa> findByCliente_IdAndChefe_Id(UUID clienteId, UUID chefeId);

    /** SELECT ... FOR UPDATE: envios na mesma conversa passam um de cada vez (dedupe da notificação seguro). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversa c WHERE c.id = :id")
    Optional<Conversa> travarPorId(@Param("id") Long id);

    /** Os dois participantes vêm no mesmo SELECT: montar a lista não dispara uma query por conversa. */
    @EntityGraph(attributePaths = {"cliente", "chefe"})
    Page<Conversa> findByCliente_IdOrderByUltimaMensagemEmDescIdDesc(UUID clienteId, Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "chefe"})
    Page<Conversa> findByChefe_IdOrderByUltimaMensagemEmDescIdDesc(UUID chefeId, Pageable pageable);

    /** Trava as conversas do cliente (sempre na mesma ordem): um envio em andamento termina antes da exclusão. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversa c WHERE c.cliente.id = :clienteId ORDER BY c.id")
    List<Conversa> travarDoCliente(@Param("clienteId") UUID clienteId);

    void deleteByCliente_Id(UUID clienteId);
}
