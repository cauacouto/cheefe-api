package com.couto.chefe_api.repositorys;

import com.couto.chefe_api.domin.DenunciaMensagem;
import com.couto.chefe_api.enums.StatusDenuncia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DenunciaMensagemRepository extends JpaRepository<DenunciaMensagem, Long> {

    boolean existsByMensagem_IdAndDenuncianteId(Long mensagemId, UUID denuncianteId);

    List<DenunciaMensagem> findByMensagem_IdAndStatus(Long mensagemId, StatusDenuncia status);

    Page<DenunciaMensagem> findByStatusOrderByCriadaEmAscIdAsc(StatusDenuncia status, Pageable pageable);

    void deleteByMensagem_Conversa_Cliente_Id(UUID clienteId);
}
