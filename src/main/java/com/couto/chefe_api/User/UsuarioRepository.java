package com.couto.chefe_api.User;

import com.couto.chefe_api.domin.Agendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<UserModel, UUID> {


    Optional<UserModel> findByEmail(String email);




}
