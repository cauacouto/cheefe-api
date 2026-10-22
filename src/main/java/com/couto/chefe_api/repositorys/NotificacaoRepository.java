package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.Notificacao;
import com.couto.chefe_api.enums.EventoNotificacao;
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

    boolean existsByDestinatarioIdAndEventoAndReferenciaIdAndLidaFalse(UUID destinatarioId, EventoNotificacao evento,
                                                                       String referenciaId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notificacao n SET n.lida = true WHERE n.destinatarioId = :destinatarioId AND n.evento = :evento "
            + "AND n.referenciaId = :referenciaId AND n.lida = false")
    void marcarLidasDaReferencia(@Param("destinatarioId") UUID destinatarioId, @Param("evento") EventoNotificacao evento,
                                 @Param("referenciaId") String referenciaId);

    @Modifying
    @Query("UPDATE Notificacao n SET n.lida = true WHERE n.destinatarioId = :destinatarioId AND n.lida = false")
    void marcarTodasLidas(@Param("destinatarioId") UUID destinatarioId);
}
