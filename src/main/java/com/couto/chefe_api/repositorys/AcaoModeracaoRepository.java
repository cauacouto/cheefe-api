package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.AcaoModeracao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcaoModeracaoRepository extends JpaRepository<AcaoModeracao, Long> {

    Page<AcaoModeracao> findAllByOrderByRealizadaEmDescIdDesc(Pageable pageable);
}
