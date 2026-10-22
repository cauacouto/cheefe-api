package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.Denuncia;
import com.couto.chefe_api.enums.StatusDenuncia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DenunciaRepository extends JpaRepository<Denuncia, Long> {

    boolean existsByCliente_IdAndChefe_IdAndStatus(UUID clienteId, UUID chefeId, StatusDenuncia status);

    @Query("SELECT COUNT(DISTINCT d.cliente.id) FROM Denuncia d WHERE d.chefe.id = :chefeId AND d.status = :status")
    long countDistinctClienteByChefeIdAndStatus(@Param("chefeId") UUID chefeId, @Param("status") StatusDenuncia status);

    List<Denuncia> findByChefe_IdAndStatus(UUID chefeId, StatusDenuncia status);

    Page<Denuncia> findByStatusOrderByCriadaEmAscIdAsc(StatusDenuncia status, Pageable pageable);
}
