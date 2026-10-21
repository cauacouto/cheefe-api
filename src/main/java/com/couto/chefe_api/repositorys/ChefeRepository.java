package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.ChefeModel;
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

    // visível = ativo, disponível e perfil completo (mesma regra de ChefeModel.isPerfilCompleto)
    String VISIVEL = "c.ativo = true AND c.disponivel = true " +
            "AND TRIM(COALESCE(c.nacionalidade, '')) <> '' AND c.especialidade IS NOT NULL " +
            "AND c.senioridade IS NOT NULL AND c.anosDeExperiencia IS NOT NULL " +
            "AND TRIM(COALESCE(c.idiomas, '')) <> '' AND c.valorHora IS NOT NULL";

    @Query(value = "SELECT c FROM ChefeModel c LEFT JOIN c.agendamentos a WHERE " + VISIVEL +
            " GROUP BY c ORDER BY COUNT(a) DESC",
            countQuery = "SELECT COUNT(c) FROM ChefeModel c WHERE " + VISIVEL)
    Page<ChefeModel> findMaisAgendados(Pageable pageable);
}
