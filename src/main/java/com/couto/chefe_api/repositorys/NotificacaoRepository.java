package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.Notificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    Page<Notificacao> findByDestinatarioIdOrderByCriadaEmDescIdDesc(UUID destinatarioId, Pageable pageable);

    Page<Notificacao> findByDestinatarioIdAndLidaFalseOrderByCriadaEmDescIdDesc(UUID destinatarioId, Pageable pageable);

    long countByDestinatarioIdAndLidaFalse(UUID destinatarioId);

    Optional<Notificacao> findByIdAndDestinatarioId(Long id, UUID destinatarioId);

    @Modifying
    @Query("UPDATE Notificacao n SET n.lida = true WHERE n.destinatarioId = :destinatarioId AND n.lida = false")
    void marcarTodasLidas(@Param("destinatarioId") UUID destinatarioId);
}
