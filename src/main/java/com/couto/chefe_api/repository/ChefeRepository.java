package com.couto.chefe_api.repository;

import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.couto.chefe_api.domain.ChefeModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChefeRepository extends JpaRepository<ChefeModel, UUID>, JpaSpecificationExecutor<ChefeModel> {

    Optional<ChefeModel> findByEmailIgnoreCase(String email);

    // visível = ativo, disponível, perfil completo, não suspenso e < 3 denunciantes com denúncia aberta
    String VISIVEL = "c.ativo = true AND c.disponivel = true " +
            "AND TRIM(COALESCE(c.nacionalidade, '')) <> '' AND c.especialidade IS NOT NULL " +
            "AND c.senioridade IS NOT NULL AND c.anosDeExperiencia IS NOT NULL " +
            "AND TRIM(COALESCE(c.idiomas, '')) <> '' AND c.valorHora IS NOT NULL " +
            "AND c.suspenso = false " +
            "AND (SELECT COUNT(DISTINCT d.cliente.id) FROM Denuncia d WHERE d.chefe = c " +
            "AND d.status = com.couto.chefe_api.enums.StatusDenuncia.ABERTA) < 3";

    /** SELECT ... FOR UPDATE sempre na mesma ordem (por id): confirmações simultâneas esperam e não travam umas às outras. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChefeModel c WHERE c.id IN :ids ORDER BY c.id")
    List<ChefeModel> travar(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT c FROM ChefeModel c LEFT JOIN c.agendamentos a WHERE " + VISIVEL +
            " GROUP BY c ORDER BY COUNT(a) DESC",
            countQuery = "SELECT COUNT(c) FROM ChefeModel c WHERE " + VISIVEL)
    Page<ChefeModel> findMaisAgendados(Pageable pageable);
}
