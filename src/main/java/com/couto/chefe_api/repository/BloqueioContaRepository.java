package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.BloqueioConta;
import com.couto.chefe_api.enums.StatusBloqueio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BloqueioContaRepository extends JpaRepository<BloqueioConta, Long> {

    Page<BloqueioConta> findByStatusOrderByCriadoEmAscIdAsc(StatusBloqueio status, Pageable pageable);

    void deleteByContaId(UUID contaId);
}
