package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.Agendamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendamentoRepository extends JpaRepository<Agendamento,Long> {


    List<Agendamento> findAllByUsuario_Id(UUID usuario);

    Optional<Agendamento> findByIdAndUsuario_Id(Long id, UUID usuarioId);

    @Query("SELECT a.id FROM Agendamento a WHERE a.usuario.id = :clienteId")
    List<Long> idsDoCliente(@Param("clienteId") UUID clienteId);

    /** Do cliente, data mais distante primeiro; cliente e endereço no mesmo SELECT (sem N+1). */
    @EntityGraph(attributePaths = {"usuario", "endereco"})
    Page<Agendamento> findByUsuario_IdOrderByDataHoraInicialDescIdDesc(UUID usuarioId, Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "endereco"})
    @Query(value = "SELECT a FROM Agendamento a JOIN a.chefes c WHERE c.id = :chefeId "
            + "ORDER BY a.dataHoraInicial DESC, a.id DESC",
            countQuery = "SELECT COUNT(a) FROM Agendamento a JOIN a.chefes c WHERE c.id = :chefeId")
    Page<Agendamento> findRecebidos(@Param("chefeId") UUID chefeId, Pageable pageable);




}
