package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.Pratos;
import jdk.dynalink.linker.LinkerServices;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PatrosRepository extends JpaRepository<Pratos , Integer> {

    List<Pratos> findByChefeModelId(UUID id);
}
