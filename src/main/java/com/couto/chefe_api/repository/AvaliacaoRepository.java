package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.Avaliacao;
import com.couto.chefe_api.enums.TipoUsuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    boolean existsByAgendamento_IdAndAutorIdAndAvaliadoId(Long agendamentoId, UUID autorId, UUID avaliadoId);

    /** O comentário com nome do cliente sobre o chef, ainda esperando o chef avaliá-lo. */
    Optional<Avaliacao> findByAgendamento_IdAndAutorIdAndAvaliadoIdAndPublicadoEmIsNull(Long agendamentoId, UUID autorId,
                                                                                       UUID avaliadoId);

    Page<Avaliacao> findByAvaliadoIdAndAvaliadoTipoAndPublicadoEmIsNotNullOrderByPublicadoEmDescIdDesc(
            UUID avaliadoId, TipoUsuario avaliadoTipo, Pageable pageable);

    /** Comentários de chefs sobre clientes: só a moderação lê. */
    Page<Avaliacao> findByAvaliadoTipoAndComentarioIsNotNullOrderByCriadaEmDescIdDesc(TipoUsuario avaliadoTipo,
                                                                                      Pageable pageable);

    List<Avaliacao> findByAutorIdAndAvaliadoTipo(UUID autorId, TipoUsuario avaliadoTipo);

    /** Avaliações dos agendamentos do cliente, nas duas direções (somem com a conta). */
    @Query("SELECT a.id FROM Avaliacao a WHERE a.agendamento.usuario.id = :clienteId")
    List<Long> idsDosAgendamentosDoCliente(@Param("clienteId") UUID clienteId);

    List<Avaliacao> findByAutorIdOrAvaliadoId(UUID autorId, UUID avaliadoId);

    /** Só publica o que ainda está esperando: o chef pode ter avaliado o cliente (e publicado) nesse meio-tempo. */
    @Modifying
    @Query("UPDATE Avaliacao a SET a.publicadoEm = :agora WHERE a.id = :id AND a.publicadoEm IS NULL")
    int publicarSeEsperando(@Param("id") Long id, @Param("agora") LocalDateTime agora);

    /** Comentários com nome cujo prazo de espera acabou (a rotina publica). */
    @Query("SELECT a.id FROM Avaliacao a WHERE a.anonimo = false AND a.publicadoEm IS NULL "
            + "AND a.agendamento.concluidoEm <= :limite")
    List<Long> idsComNomeParaPublicar(@Param("limite") LocalDateTime limite);

    /** Linhas {tag, quantidade}: só agrega, sem ligar tag a autor. */
    @Query("SELECT t, COUNT(a) FROM Avaliacao a JOIN a.tags t "
            + "WHERE a.avaliadoId = :avaliadoId AND a.avaliadoTipo = :avaliadoTipo GROUP BY t")
    List<Object[]> contagemDeTags(@Param("avaliadoId") UUID avaliadoId, @Param("avaliadoTipo") TipoUsuario avaliadoTipo);
}
