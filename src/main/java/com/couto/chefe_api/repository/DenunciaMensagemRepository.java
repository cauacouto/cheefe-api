package com.couto.chefe_api.repository;

import com.couto.chefe_api.domain.DenunciaMensagem;
import com.couto.chefe_api.enums.StatusDenuncia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DenunciaMensagemRepository extends JpaRepository<DenunciaMensagem, Long> {

    boolean existsByMensagem_IdAndDenuncianteId(Long mensagemId, UUID denuncianteId);

    List<DenunciaMensagem> findByMensagem_IdAndStatus(Long mensagemId, StatusDenuncia status);

    Page<DenunciaMensagem> findByStatusOrderByCriadaEmAscIdAsc(StatusDenuncia status, Pageable pageable);

    @Query("SELECT d.id FROM DenunciaMensagem d WHERE d.mensagem.conversa.cliente.id = :clienteId")
    List<Long> idsDasDenunciasNasConversasDoCliente(@Param("clienteId") UUID clienteId);

    void deleteByMensagem_Conversa_Cliente_Id(UUID clienteId);
}
