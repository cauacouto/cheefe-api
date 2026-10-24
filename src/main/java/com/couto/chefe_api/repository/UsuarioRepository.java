package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.domain.Agendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<UserModel, UUID> {


    Optional<UserModel> findByEmailIgnoreCase(String email);

    boolean existsByIdAndBloqueadoEmIsNotNull(UUID id);

    boolean existsByIdAndTelefoneVerificadoIsNotNull(UUID id);

    /** O telefone já está verificado em outra conta desta tabela (a própria conta não conta). */
    boolean existsByTelefoneVerificadoAndIdNot(String telefoneVerificado, UUID id);

    /** Soma atômica no banco: avaliações simultâneas da mesma pessoa não perdem contagem. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE UserModel u SET u.somaNotas = u.somaNotas + :nota, u.totalAvaliacoes = u.totalAvaliacoes + 1 WHERE u.id = :id")
    int somarAvaliacao(@Param("id") UUID id, @Param("nota") int nota);




}
