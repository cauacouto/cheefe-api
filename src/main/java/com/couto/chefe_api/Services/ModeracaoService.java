package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.DenunciaModeracaoDto;
import com.couto.chefe_api.Excepitons.ChefeNaoEncontradoException;
import com.couto.chefe_api.Excepitons.ConflitoException;
import com.couto.chefe_api.Excepitons.RecursoNaoEncontradoException;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.Denuncia;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.DenunciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModeracaoService {

    private final DenunciaRepository denunciaRepository;
    private final ChefeRepository chefeRepository;
    private final NotificacaoService notificacaoService;

    @Transactional(readOnly = true)
    public Page<DenunciaModeracaoDto> listar(StatusDenuncia status, Pageable pageable) {
        return denunciaRepository.findByStatusOrderByCriadaEmAscIdAsc(status, pageable).map(DenunciaModeracaoDto::de);
    }

    @Transactional
    public void arquivar(Long denunciaId, String moderadorEmail) {
        Denuncia denuncia = denunciaRepository.findById(denunciaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("denúncia não encontrada"));
        if (denuncia.getStatus() != StatusDenuncia.ABERTA) {
            throw new ConflitoException("denúncia já resolvida");
        }
        resolver(denuncia, StatusDenuncia.ARQUIVADA, moderadorEmail);
    }

    @Transactional
    public void suspender(UUID chefeId, String moderadorEmail) {
        ChefeModel chefe = buscarChefe(chefeId);
        chefe.setSuspenso(true);
        fecharAbertasComoProcedentes(chefeId, moderadorEmail);
        notificarChefe(chefe, EventoNotificacao.CHEF_SUSPENSO, "Seu perfil foi suspenso");
    }

    @Transactional
    public void reativar(UUID chefeId) {
        ChefeModel chefe = buscarChefe(chefeId);
        chefe.setSuspenso(false);
        notificarChefe(chefe, EventoNotificacao.CHEF_REATIVADO, "Seu perfil foi reativado");
    }

    /** Banir = conta apagada: não loga nem opera mais (ChefeModel.exigirAtivo). */
    @Transactional
    public void banir(UUID chefeId, String moderadorEmail) {
        ChefeModel chefe = buscarChefe(chefeId);
        chefe.setAtivo(false);
        fecharAbertasComoProcedentes(chefeId, moderadorEmail);
    }

    private void fecharAbertasComoProcedentes(UUID chefeId, String moderadorEmail) {
        denunciaRepository.findByChefe_IdAndStatus(chefeId, StatusDenuncia.ABERTA)
                .forEach(denuncia -> resolver(denuncia, StatusDenuncia.PROCEDENTE, moderadorEmail));
    }

    private static void resolver(Denuncia denuncia, StatusDenuncia status, String moderadorEmail) {
        denuncia.setStatus(status);
        denuncia.setResolvidaEm(LocalDateTime.now());
        denuncia.setResolvidaPor(moderadorEmail);
    }

    private void notificarChefe(ChefeModel chefe, EventoNotificacao evento, String mensagem) {
        notificacaoService.notificar(chefe.getId(), TipoUsuario.CHEFE, evento, mensagem, chefe.getId().toString());
    }

    private ChefeModel buscarChefe(UUID chefeId) {
        return chefeRepository.findById(chefeId).orElseThrow(ChefeNaoEncontradoException::new);
    }
}
