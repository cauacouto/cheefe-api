package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.Agendamento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgendamentoRepository extends JpaRepository<Agendamento,Long> {
}
