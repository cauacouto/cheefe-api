package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.DenunciaAvaliacao;
import com.couto.chefe_api.enums.StatusDenuncia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DenunciaAvaliacaoRepository extends JpaRepository<DenunciaAvaliacao, Long> {

    boolean existsByAvaliacao_IdAndDenuncianteId(Long avaliacaoId, UUID denuncianteId);

    List<DenunciaAvaliacao> findByAvaliacao_IdAndStatus(Long avaliacaoId, StatusDenuncia status);

    Page<DenunciaAvaliacao> findByStatusOrderByCriadaEmAscIdAsc(StatusDenuncia status, Pageable pageable);
}
