package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.AgendaDiaFixo;
import com.couto.chefe_api.domain.AgendaDiaFixoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AgendaDiaFixoRepository extends JpaRepository<AgendaDiaFixo, AgendaDiaFixoId> {

    @Query("SELECT d FROM AgendaDiaFixo d WHERE d.id.chefeId = :chefeId")
    List<AgendaDiaFixo> doChefe(@Param("chefeId") UUID chefeId);
}
