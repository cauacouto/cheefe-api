package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.Conversa;
import jakarta.persistence.LockModeType;
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

    List<Conversa> findByCliente_IdOrderByUltimaMensagemEmDesc(UUID clienteId);

    List<Conversa> findByChefe_IdOrderByUltimaMensagemEmDesc(UUID chefeId);

    @Query("SELECT c.id FROM Conversa c WHERE c.cliente.id = :clienteId")
    List<Long> idsDoCliente(@Param("clienteId") UUID clienteId);

    void deleteByCliente_Id(UUID clienteId);
}
