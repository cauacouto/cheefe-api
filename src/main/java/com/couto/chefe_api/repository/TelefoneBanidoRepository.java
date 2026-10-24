package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.TelefoneBanido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelefoneBanidoRepository extends JpaRepository<TelefoneBanido, String>, TelefoneBanidoRepositoryCustom {
}
