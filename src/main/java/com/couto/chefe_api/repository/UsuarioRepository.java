package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.domain.Agendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<UserModel, UUID> {


    Optional<UserModel> findByEmailIgnoreCase(String email);

    boolean existsByIdAndBloqueadoEmIsNotNull(UUID id);




}
