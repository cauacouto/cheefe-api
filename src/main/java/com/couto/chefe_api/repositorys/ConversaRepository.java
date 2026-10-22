package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.Conversa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversaRepository extends JpaRepository<Conversa, Long> {

    Optional<Conversa> findByCliente_IdAndChefe_Id(UUID clienteId, UUID chefeId);

    List<Conversa> findByCliente_IdOrderByUltimaMensagemEmDesc(UUID clienteId);

    List<Conversa> findByChefe_IdOrderByUltimaMensagemEmDesc(UUID chefeId);

    @Query("SELECT c.id FROM Conversa c WHERE c.cliente.id = :clienteId")
    List<Long> idsDoCliente(@Param("clienteId") UUID clienteId);

    void deleteByCliente_Id(UUID clienteId);
}
