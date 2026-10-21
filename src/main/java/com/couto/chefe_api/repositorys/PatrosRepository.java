package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.Pratos;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatrosRepository extends JpaRepository<Pratos , Integer> {

    List<Pratos> findByChefeModelId(UUID id);

    Optional<Pratos> findByIdAndChefeModel_Id(Integer id, UUID chefeId);
}
