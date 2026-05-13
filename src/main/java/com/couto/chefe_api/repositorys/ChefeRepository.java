package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.ChefeModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChefeRepository extends JpaRepository<ChefeModel, UUID> {


    List<ChefeModel> findByAtivoTrue();



    List<ChefeModel> findByDisponivelTrue();


}
