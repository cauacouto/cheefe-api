package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.AcaoModeracaoDto;
import com.couto.chefe_api.Dtos.DenunciaMensagemModeracaoDto;
import com.couto.chefe_api.Dtos.DenunciaModeracaoDto;
import com.couto.chefe_api.Excepitons.ChefeNaoEncontradoException;
import com.couto.chefe_api.Excepitons.ConflitoException;
import com.couto.chefe_api.Excepitons.RecursoNaoEncontradoException;
import com.couto.chefe_api.domin.AcaoModeracao;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.Services.eventos.MensagemApagadaEvent;
import com.couto.chefe_api.domin.Conversa;
import com.couto.chefe_api.domin.Denuncia;
import com.couto.chefe_api.domin.DenunciaMensagem;
import com.couto.chefe_api.domin.Mensagem;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.enums.TipoAcaoModeracao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.AcaoModeracaoRepository;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.DenunciaMensagemRepository;
import com.couto.chefe_api.repositorys.DenunciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModeracaoService {

    private final DenunciaRepository denunciaRepository;
    private final ChefeRepository chefeRepository;
    private final NotificacaoService notificacaoService;
    private final AcaoModeracaoRepository acaoModeracaoRepository;
    private final DenunciaMensagemRepository denunciaMensagemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

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
        registrar(moderadorEmail, TipoAcaoModeracao.ARQUIVAR_DENUNCIA, denuncia.getChefe().getId(), denunciaId);
    }

    @Transactional
    public void suspender(UUID chefeId, String moderadorEmail) {
        ChefeModel chefe = buscarChefe(chefeId);
        chefe.setSuspenso(true);
        fecharAbertasComoProcedentes(chefeId, moderadorEmail);
        notificarChefe(chefe, EventoNotificacao.CHEF_SUSPENSO, "Seu perfil foi suspenso");
        registrar(moderadorEmail, TipoAcaoModeracao.SUSPENDER_CHEF, chefeId, null);
    }

    @Transactional
    public void reativar(UUID chefeId, String moderadorEmail) {
        ChefeModel chefe = buscarChefe(chefeId);
        chefe.setSuspenso(false);
        notificarChefe(chefe, EventoNotificacao.CHEF_REATIVADO, "Seu perfil foi reativado");
        registrar(moderadorEmail, TipoAcaoModeracao.REATIVAR_CHEF, chefeId, null);
    }

    /** Banir = conta apagada: não loga nem opera mais (ChefeModel.exigirAtivo). */
    @Transactional
    public void banir(UUID chefeId, String moderadorEmail) {
        ChefeModel chefe = buscarChefe(chefeId);
        chefe.setAtivo(false);
        fecharAbertasComoProcedentes(chefeId, moderadorEmail);
        registrar(moderadorEmail, TipoAcaoModeracao.BANIR_CHEF, chefeId, null);
    }

    @Transactional(readOnly = true)
    public Page<DenunciaMensagemModeracaoDto> listarDenunciasMensagens(StatusDenuncia status, Pageable pageable) {
        return denunciaMensagemRepository.findByStatusOrderByCriadaEmAscIdAsc(status, pageable)
                .map(DenunciaMensagemModeracaoDto::de);
    }

    @Transactional
    public void arquivarDenunciaMensagem(Long denunciaId, String moderadorEmail) {
        DenunciaMensagem denuncia = buscarDenunciaMensagemAberta(denunciaId);
        resolver(denuncia, StatusDenuncia.ARQUIVADA, moderadorEmail);
        registrar(moderadorEmail, TipoAcaoModeracao.ARQUIVAR_DENUNCIA_MENSAGEM, null, denunciaId);
    }

    /** Some para os dois participantes (o texto continua guardado); todas as denúncias abertas dela procedem. */
    @Transactional
    public void removerMensagem(Long denunciaId, String moderadorEmail) {
        DenunciaMensagem denuncia = buscarDenunciaMensagemAberta(denunciaId);
        Mensagem mensagem = denuncia.getMensagem();
        mensagem.setRemovidaPorModeracao(true);
        resolver(denuncia, StatusDenuncia.PROCEDENTE, moderadorEmail);
        denunciaMensagemRepository.findByMensagem_IdAndStatus(mensagem.getId(), StatusDenuncia.ABERTA)
                .forEach(outra -> resolver(outra, StatusDenuncia.PROCEDENTE, moderadorEmail));
        registrar(moderadorEmail, TipoAcaoModeracao.REMOVER_MENSAGEM, null, denunciaId);
        Conversa conversa = mensagem.getConversa();
        eventPublisher.publishEvent(new MensagemApagadaEvent(conversa.getId(), mensagem.getId(),
                conversa.getCliente().getId(), conversa.getChefe().getId()));
    }

    @Transactional(readOnly = true)
    public Page<AcaoModeracaoDto> historico(Pageable pageable) {
        return acaoModeracaoRepository.findAllByOrderByRealizadaEmDescIdDesc(pageable).map(AcaoModeracaoDto::de);
    }

    /** Toda ação da moderação deixa rastro de quem fez (mesma transação: ação desfeita = sem registro). */
    private void registrar(String moderadorEmail, TipoAcaoModeracao tipo, UUID chefeId, Long denunciaId) {
        AcaoModeracao acao = new AcaoModeracao();
        acao.setModeradorEmail(moderadorEmail);
        acao.setAcao(tipo);
        acao.setChefeId(chefeId);
        acao.setDenunciaId(denunciaId);
        acao.setRealizadaEm(LocalDateTime.now(clock));
        acaoModeracaoRepository.save(acao);
        log.info("moderação: {} por {} (chef={}, denúncia={})", tipo, moderadorEmail, chefeId, denunciaId);
    }

    private void fecharAbertasComoProcedentes(UUID chefeId, String moderadorEmail) {
        denunciaRepository.findByChefe_IdAndStatus(chefeId, StatusDenuncia.ABERTA)
                .forEach(denuncia -> resolver(denuncia, StatusDenuncia.PROCEDENTE, moderadorEmail));
    }

    private void resolver(Denuncia denuncia, StatusDenuncia status, String moderadorEmail) {
        denuncia.setStatus(status);
        denuncia.setResolvidaEm(LocalDateTime.now(clock));
        denuncia.setResolvidaPor(moderadorEmail);
    }

    private void resolver(DenunciaMensagem denuncia, StatusDenuncia status, String moderadorEmail) {
        denuncia.setStatus(status);
        denuncia.setResolvidaEm(LocalDateTime.now(clock));
        denuncia.setResolvidaPor(moderadorEmail);
    }

    private DenunciaMensagem buscarDenunciaMensagemAberta(Long denunciaId) {
        DenunciaMensagem denuncia = denunciaMensagemRepository.findById(denunciaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("denúncia não encontrada"));
        if (denuncia.getStatus() != StatusDenuncia.ABERTA) {
            throw new ConflitoException("denúncia já resolvida");
        }
        return denuncia;
    }

    private void notificarChefe(ChefeModel chefe, EventoNotificacao evento, String mensagem) {
        notificacaoService.notificar(chefe.getId(), TipoUsuario.CHEFE, evento, mensagem, chefe.getId().toString());
    }

    private ChefeModel buscarChefe(UUID chefeId) {
        return chefeRepository.findById(chefeId).orElseThrow(ChefeNaoEncontradoException::new);
    }
}
