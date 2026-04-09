package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.EnderecoModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EndercoRepository extends JpaRepository<EnderecoModel,Long> {
}
