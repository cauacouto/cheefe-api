package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.ChefeModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChefeRepository extends JpaRepository<ChefeModel, UUID> {


    List<ChefeModel> findByAtivoTrue();



    List<ChefeModel> findByDisponivel();
}
