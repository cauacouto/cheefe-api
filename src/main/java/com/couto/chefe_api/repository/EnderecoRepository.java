package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.EnderecoModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EnderecoRepository extends JpaRepository<EnderecoModel,Long> {

    Optional<EnderecoModel> findByIdAndUsuario_Id(Long id, UUID usuarioId);
}
