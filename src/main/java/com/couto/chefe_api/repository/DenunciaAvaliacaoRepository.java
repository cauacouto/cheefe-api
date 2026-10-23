package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.DenunciaAvaliacao;
import com.couto.chefe_api.enums.StatusDenuncia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DenunciaAvaliacaoRepository extends JpaRepository<DenunciaAvaliacao, Long> {

    @Query("SELECT d.id FROM DenunciaAvaliacao d WHERE d.avaliacao.agendamento.usuario.id = :clienteId")
    List<Long> idsDosAgendamentosDoCliente(@Param("clienteId") UUID clienteId);

    boolean existsByAvaliacao_IdAndDenuncianteId(Long avaliacaoId, UUID denuncianteId);

    List<DenunciaAvaliacao> findByAvaliacao_IdAndStatus(Long avaliacaoId, StatusDenuncia status);

    /** SELECT ... FOR UPDATE: dois moderadores não resolvem a mesma denúncia ao mesmo tempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DenunciaAvaliacao d WHERE d.id = :id")
    Optional<DenunciaAvaliacao> travarPorId(@Param("id") Long id);

    /** A avaliação vem junto: a fila mostra o original e a nota sem uma consulta por linha. */
    @EntityGraph(attributePaths = "avaliacao")
    Page<DenunciaAvaliacao> findByStatusOrderByCriadaEmAscIdAsc(StatusDenuncia status, Pageable pageable);
}
