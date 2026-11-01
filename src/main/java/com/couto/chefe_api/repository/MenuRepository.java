package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    Optional<Menu> findByIdAndChefe_Id(Long id, UUID chefeId);

    List<Menu> findByChefe_IdOrderByIdAsc(UUID chefeId);

    List<Menu> findByChefe_IdAndAtivoTrueOrderByIdAsc(UUID chefeId);

    /** Conta os pausados também: o limite é de menus cadastrados. */
    long countByChefe_Id(UUID chefeId);

    boolean existsByChefe_IdAndAtivoTrue(UUID chefeId);

    /** Menor preço por pessoa entre os menus ativos de cada chef: uma consulta para a página inteira. Sem menu ativo, sem linha. */
    @Query("SELECT m.chefe.id AS chefeId, MIN(m.precoPorPessoa) AS minimo FROM Menu m " +
            "WHERE m.chefe.id IN :chefes AND m.ativo = true GROUP BY m.chefe.id")
    List<MenorPreco> menoresPrecos(@Param("chefes") Collection<UUID> chefes);

    interface MenorPreco {
        UUID getChefeId();

        BigDecimal getMinimo();
    }
}
