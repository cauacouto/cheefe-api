package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.Agendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AgendamentoRepository extends JpaRepository<Agendamento,Long> {


    List<Agendamento> findAllByUsuario_Id(UUID usuario);

    @Query("SELECT a FROM Agendamento a JOIN a.chefes c WHERE c.id = :chefeId")
    List<Agendamento> findAllByChefe(@Param("chefeId") UUID chefeId);


}
