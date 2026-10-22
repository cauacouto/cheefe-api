package com.couto.chefe_api.repository;

import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import com.couto.chefe_api.domain.Agendamento;
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

    @Query("SELECT a.id FROM Agendamento a WHERE a.status = com.couto.chefe_api.enums.StatusAgendamento.CONFIRMADO "
            + "AND a.dataHoraFinal <= :agora")
    List<Long> idsParaConcluir(@Param("agora") LocalDateTime agora);

    @Query("SELECT a.id FROM Agendamento a WHERE a.status = com.couto.chefe_api.enums.StatusAgendamento.PENDENTE "
            + "AND a.dataHoraInicial <= :agora")
    List<Long> idsParaExpirar(@Param("agora") LocalDateTime agora);

    /** SELECT ... FOR UPDATE: a rotina e o chef não concluem o mesmo agendamento ao mesmo tempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Agendamento a WHERE a.id = :id")
    Optional<Agendamento> travarPorId(@Param("id") Long id);

    /** Intervalos semiabertos: um que termina às 20h não conflita com outro que começa às 20h. */
    @Query("SELECT COUNT(a) > 0 FROM Agendamento a JOIN a.chefes c WHERE c.id = :chefeId "
            + "AND a.status = com.couto.chefe_api.enums.StatusAgendamento.CONFIRMADO "
            + "AND a.dataHoraInicial < :fim AND a.dataHoraFinal > :inicio AND a.id <> :ignorarId")
    boolean existeConfirmadoSobreposto(@Param("chefeId") UUID chefeId, @Param("inicio") LocalDateTime inicio,
                                       @Param("fim") LocalDateTime fim, @Param("ignorarId") Long ignorarId);

    /** Do cliente, data mais distante primeiro; cliente e endereço no mesmo SELECT (sem N+1). */
    @EntityGraph(attributePaths = {"usuario", "endereco"})
    Page<Agendamento> findByUsuario_IdOrderByDataHoraInicialDescIdDesc(UUID usuarioId, Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "endereco"})
    @Query(value = "SELECT a FROM Agendamento a JOIN a.chefes c WHERE c.id = :chefeId "
            + "ORDER BY a.dataHoraInicial DESC, a.id DESC",
            countQuery = "SELECT COUNT(a) FROM Agendamento a JOIN a.chefes c WHERE c.id = :chefeId")
    Page<Agendamento> findRecebidos(@Param("chefeId") UUID chefeId, Pageable pageable);




}
