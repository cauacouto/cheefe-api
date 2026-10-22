package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.Mensagem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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
            + "AND m.remetenteId <> :leitorId AND m.lidaEm IS NULL")
    int marcarLidas(@Param("conversaId") Long conversaId, @Param("leitorId") UUID leitorId,
                    @Param("agora") LocalDateTime agora);

    void deleteByConversa_Cliente_Id(UUID clienteId);
}
