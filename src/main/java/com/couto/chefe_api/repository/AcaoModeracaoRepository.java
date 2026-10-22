package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.AcaoModeracao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcaoModeracaoRepository extends JpaRepository<AcaoModeracao, Long> {

    Page<AcaoModeracao> findAllByOrderByRealizadaEmDescIdDesc(Pageable pageable);
}
