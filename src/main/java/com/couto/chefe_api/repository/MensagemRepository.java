package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.Mensagem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    List<Mensagem> findByConversa_IdOrderByIdDesc(Long conversaId, Pageable pageable);

    List<Mensagem> findByConversa_IdAndIdLessThanOrderByIdDesc(Long conversaId, Long antesDe, Pageable pageable);

    Optional<Mensagem> findFirstByConversa_IdOrderByIdDesc(Long conversaId);

    Optional<Mensagem> findByIdAndConversa_Id(Long id, Long conversaId);

    long countByConversa_IdAndRemetenteIdNotAndLidaEmIsNull(Long conversaId, UUID leitorId);

    /** Marca como lidas as mensagens recebidas (do outro participante) ainda não lidas; devolve a quantidade. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Mensagem m SET m.lidaEm = :agora WHERE m.conversa.id = :conversaId "
            + "AND m.id <= :ateId AND m.remetenteId <> :leitorId AND m.lidaEm IS NULL")
    int marcarLidas(@Param("conversaId") Long conversaId, @Param("leitorId") UUID leitorId, @Param("ateId") Long ateId,
                    @Param("agora") LocalDateTime agora);

    /** Última mensagem de cada conversa da página, numa query só. */
    @Query("SELECT m FROM Mensagem m WHERE m.id IN "
            + "(SELECT MAX(m2.id) FROM Mensagem m2 WHERE m2.conversa.id IN :conversas GROUP BY m2.conversa.id)")
    List<Mensagem> ultimasDasConversas(@Param("conversas") Collection<Long> conversas);

    interface NaoLidas {
        Long getConversaId();

        long getTotal();
    }

    @Query("SELECT m.conversa.id AS conversaId, COUNT(m) AS total FROM Mensagem m WHERE m.conversa.id IN :conversas "
            + "AND m.remetenteId <> :leitorId AND m.lidaEm IS NULL GROUP BY m.conversa.id")
    List<NaoLidas> naoLidasPorConversa(@Param("conversas") Collection<Long> conversas, @Param("leitorId") UUID leitorId);

    void deleteByConversa_Cliente_Id(UUID clienteId);
}
