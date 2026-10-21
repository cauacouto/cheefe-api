package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.ChefeModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChefeRepository extends JpaRepository<ChefeModel, UUID>, JpaSpecificationExecutor<ChefeModel> {

    Optional<ChefeModel> findByEmail(String usuarioLogado);

    @Query(value = "SELECT c FROM ChefeModel c LEFT JOIN c.agendamentos a " +
            "WHERE c.ativo = true GROUP BY c ORDER BY COUNT(a) DESC",
            countQuery = "SELECT COUNT(c) FROM ChefeModel c WHERE c.ativo = true")
    Page<ChefeModel> findMaisAgendados(Pageable pageable);
}
