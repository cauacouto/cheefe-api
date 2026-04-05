package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.ChefeModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface repositoryChefe extends JpaRepository<ChefeModel, UUID> {
}
