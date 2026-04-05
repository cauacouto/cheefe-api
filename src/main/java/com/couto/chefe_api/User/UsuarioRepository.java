package com.couto.chefe_api.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<UserModel, UUID> {
}
