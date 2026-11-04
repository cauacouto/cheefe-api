package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.NotaCliente;
import com.couto.chefe_api.domain.NotaClienteId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotaClienteRepository extends JpaRepository<NotaCliente, NotaClienteId> {

    /** Conta apagada: as notas do chef somem com ela. */
    void deleteByChefe_Id(UUID chefeId);
}
