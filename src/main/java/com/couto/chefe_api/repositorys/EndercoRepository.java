package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.EnderecoModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EndercoRepository extends JpaRepository<EnderecoModel,Long> {

    Optional<EnderecoModel> findByIdAndUsuario_Id(Long id, UUID usuarioId);
}
