package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
