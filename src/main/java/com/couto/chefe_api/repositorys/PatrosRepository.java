package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.Pratos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatrosRepository extends JpaRepository<Pratos , Integer> {

    /** Pratos do próprio chef (inclusive pausado): é dele, ele sempre vê. */
    @EntityGraph(attributePaths = "chefeModel")
    List<Pratos> findByChefeModelId(UUID id);

    /** Vitrine pública: só pratos de chefs visíveis (mesma regra da busca de chefs). */
    @Query(value = "SELECT p FROM Pratos p JOIN FETCH p.chefeModel c WHERE " + ChefeRepository.VISIVEL + " ORDER BY p.id DESC",
            countQuery = "SELECT COUNT(p) FROM Pratos p JOIN p.chefeModel c WHERE " + ChefeRepository.VISIVEL)
    Page<Pratos> findVisiveis(Pageable pageable);

    @Query("SELECT p FROM Pratos p JOIN FETCH p.chefeModel c WHERE c.id = :chefeId AND " + ChefeRepository.VISIVEL
            + " ORDER BY p.id DESC")
    List<Pratos> findVisiveisDoChefe(@Param("chefeId") UUID chefeId);

    Optional<Pratos> findByIdAndChefeModel_Id(Integer id, UUID chefeId);
}
