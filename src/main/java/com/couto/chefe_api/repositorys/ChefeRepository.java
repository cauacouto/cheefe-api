package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.ChefeModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChefeRepository extends JpaRepository<ChefeModel, UUID> {


    List<ChefeModel> findByAtivoTrue();



    List<ChefeModel> findByDisponivelTrue();

    Optional<ChefeModel> findByEmail(String usuarioLogado);


@Query ("SELECT c FROM ChefeModel c WHERE " +
           "(:valorMinimo IS NULL OR c.valorHora >= :valorMinimo) AND " +
           "(:valorMaximo IS NULL OR c.valorHora <= :valorMaximo)")
    Page<ChefeModel> findByFiltros(
            @Param("valorMinimo") BigDecimal valorMinimo,
            @Param("valorMaximo") BigDecimal valorMaximo,
            Pageable pageable);


    @Query("SELECT c FROM ChefeModel c LEFT JOIN c.agendamentos a " +
            "GROUP BY c ORDER BY COUNT(a) DESC")
    Page<ChefeModel> findMaisAgendados(Pageable pageable);


}
