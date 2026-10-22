package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.AgendamentoHistorico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgendamentoHistoricoRepository extends JpaRepository<AgendamentoHistorico, Long> {

    List<AgendamentoHistorico> findByAgendamento_IdOrderByOcorridoEmAscIdAsc(Long agendamentoId);
}
