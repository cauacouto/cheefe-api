package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.Avaliacao;
import com.couto.chefe_api.enums.TipoUsuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    boolean existsByAgendamento_IdAndAutorIdAndAvaliadoId(Long agendamentoId, UUID autorId, UUID avaliadoId);

    Page<Avaliacao> findByAvaliadoIdAndAvaliadoTipoAndPublicadoEmIsNotNullOrderByPublicadoEmDescIdDesc(
            UUID avaliadoId, TipoUsuario avaliadoTipo, Pageable pageable);

    List<Avaliacao> findByAutorIdOrAvaliadoId(UUID autorId, UUID avaliadoId);

    /** Comentários com nome cujo prazo de espera acabou (a rotina publica). */
    @Query("SELECT a.id FROM Avaliacao a WHERE a.anonimo = false AND a.publicadoEm IS NULL "
            + "AND a.agendamento.concluidoEm <= :limite")
    List<Long> idsComNomeParaPublicar(@Param("limite") LocalDateTime limite);

    /** Linhas {tag, quantidade}: só agrega, sem ligar tag a autor. */
    @Query("SELECT t, COUNT(a) FROM Avaliacao a JOIN a.tags t "
            + "WHERE a.avaliadoId = :avaliadoId AND a.avaliadoTipo = :avaliadoTipo GROUP BY t")
    List<Object[]> contagemDeTags(@Param("avaliadoId") UUID avaliadoId, @Param("avaliadoTipo") TipoUsuario avaliadoTipo);
}
