package com.couto.chefe_api.service;

import com.couto.chefe_api.dto.AcaoModeracaoDto;
import com.couto.chefe_api.dto.BloqueioContaDto;
import com.couto.chefe_api.domain.BloqueioConta;
import com.couto.chefe_api.enums.StatusBloqueio;
import com.couto.chefe_api.repository.BloqueioContaRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.dto.DenunciaMensagemModeracaoDto;
import com.couto.chefe_api.dto.DenunciaModeracaoDto;
import com.couto.chefe_api.exception.ChefeNaoEncontradoException;
import com.couto.chefe_api.exception.ConflitoException;
import com.couto.chefe_api.exception.RecursoNaoEncontradoException;
import com.couto.chefe_api.domain.AcaoModeracao;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.service.eventos.MensagemApagadaEvent;
import com.couto.chefe_api.domain.Conversa;
import com.couto.chefe_api.domain.Denuncia;
import com.couto.chefe_api.domain.Avaliacao;
import com.couto.chefe_api.domain.DenunciaAvaliacao;
import com.couto.chefe_api.domain.DenunciaMensagem;
import com.couto.chefe_api.dto.ComentarioClienteModeracaoDto;
import com.couto.chefe_api.dto.DenunciaAvaliacaoModeracaoDto;
import com.couto.chefe_api.repository.AvaliacaoRepository;
import com.couto.chefe_api.repository.DenunciaAvaliacaoRepository;
import com.couto.chefe_api.domain.Mensagem;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.enums.TipoAcaoModeracao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AcaoModeracaoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.DenunciaMensagemRepository;
import com.couto.chefe_api.repository.DenunciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModeracaoService {

    private final DenunciaRepository denunciaRepository;
    private final ChefeRepository chefeRepository;
    private final NotificacaoService notificacaoService;
    private final AcaoModeracaoRepository acaoModeracaoRepository;
    private final DenunciaMensagemRepository denunciaMensagemRepository;
    private final BloqueioContaRepository bloqueioContaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final DenunciaAvaliacaoRepository denunciaAvaliacaoRepository;
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
    public Page<BloqueioContaDto> listarBloqueios(StatusBloqueio status, Pageable pageable) {
        return bloqueioContaRepository.findByStatusOrderByCriadoEmAscIdAsc(status, pageable)
                .map(b -> BloqueioContaDto.de(b, nomeDaConta(b)));
    }

    /**
     * Confirma o bloqueio automático: chef passa a inativo (banido) e as denúncias abertas dele procedem;
     * a conta fica bloqueada (reaplica o bloqueio se outra decisão sobre a mesma conta o tinha limpado).
     */
    @Transactional
    public void confirmarBloqueio(Long id, String moderadorEmail) {
        BloqueioConta bloqueio = buscarBloqueioAberto(id);
        LocalDateTime agora = LocalDateTime.now(clock);
        String motivo = bloqueio.getCategoria().name();
        if (bloqueio.getContaTipo() == TipoUsuario.CHEFE) {
            chefeRepository.findById(bloqueio.getContaId()).ifPresent(c -> {
                c.setAtivo(false);
                if (c.getBloqueadoEm() == null) {
                    c.setBloqueadoEm(agora);
                    c.setBloqueioMotivo(motivo);
                }
            });
            fecharAbertasComoProcedentes(bloqueio.getContaId(), moderadorEmail);
        } else {
            usuarioRepository.findById(bloqueio.getContaId()).ifPresent(u -> {
                if (u.getBloqueadoEm() == null) {
                    u.setBloqueadoEm(agora);
                    u.setBloqueioMotivo(motivo);
                }
            });
        }
        resolver(bloqueio, StatusBloqueio.CONFIRMADO, moderadorEmail);
        registrar(moderadorEmail, TipoAcaoModeracao.CONFIRMAR_BLOQUEIO, chefeDoBloqueio(bloqueio), id);
    }

    /**
     * Reverte: a conta volta a logar e a operar (mesmo cookie volta a valer), a menos que outro bloqueio
     * da mesma conta (aberto ou confirmado) a mantenha suspensa.
     */
    @Transactional
    public void reverterBloqueio(Long id, String moderadorEmail) {
        BloqueioConta bloqueio = buscarBloqueioAberto(id);
        boolean outroBloqueio = bloqueioContaRepository.existsByContaIdAndIdNotAndStatusIn(
                bloqueio.getContaId(), id, List.of(StatusBloqueio.ABERTO, StatusBloqueio.CONFIRMADO));
        if (!outroBloqueio) {
            if (bloqueio.getContaTipo() == TipoUsuario.CHEFE) {
                chefeRepository.findById(bloqueio.getContaId()).ifPresent(c -> {
                    c.setBloqueadoEm(null);
                    c.setBloqueioMotivo(null);
                });
            } else {
                usuarioRepository.findById(bloqueio.getContaId()).ifPresent(u -> {
                    u.setBloqueadoEm(null);
                    u.setBloqueioMotivo(null);
                });
            }
        }
        resolver(bloqueio, StatusBloqueio.REVERTIDO, moderadorEmail);
        registrar(moderadorEmail, TipoAcaoModeracao.REVERTER_BLOQUEIO, chefeDoBloqueio(bloqueio), id);
    }

    private BloqueioConta buscarBloqueioAberto(Long id) {
        BloqueioConta bloqueio = bloqueioContaRepository.travarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("bloqueio não encontrado"));
        if (bloqueio.getStatus() != StatusBloqueio.ABERTO) {
            throw new ConflitoException("bloqueio já resolvido");
        }
        return bloqueio;
    }

    private void resolver(BloqueioConta bloqueio, StatusBloqueio status, String moderadorEmail) {
        bloqueio.setStatus(status);
        bloqueio.setResolvidoEm(LocalDateTime.now(clock));
        bloqueio.setResolvidoPor(moderadorEmail);
    }

    private static UUID chefeDoBloqueio(BloqueioConta bloqueio) {
        return bloqueio.getContaTipo() == TipoUsuario.CHEFE ? bloqueio.getContaId() : null;
    }

    private String nomeDaConta(BloqueioConta bloqueio) {
        if (bloqueio.getContaTipo() == TipoUsuario.CHEFE) {
            return chefeRepository.findById(bloqueio.getContaId()).map(ChefeModel::getNome).orElse(null);
        }
        return usuarioRepository.findById(bloqueio.getContaId()).map(u -> u.getNome()).orElse(null);
    }

    /** Fila de denúncias de avaliação, com o texto original e quem escreveu (mesmo em avaliação anônima). */
    @Transactional(readOnly = true)
    public Page<DenunciaAvaliacaoModeracaoDto> listarDenunciasAvaliacoes(StatusDenuncia status, Pageable pageable) {
        Page<DenunciaAvaliacao> pagina = denunciaAvaliacaoRepository.findByStatusOrderByCriadaEmAscIdAsc(status, pageable);
        Map<UUID, String> nomes = nomes(pagina.stream().flatMap(d -> Stream.of(
                Map.entry(d.getDenuncianteId(), d.getDenuncianteTipo()), Map.entry(d.getAutorId(), d.getAutorTipo()))));
        return pagina.map(d -> DenunciaAvaliacaoModeracaoDto.de(d, nomes));
    }

    @Transactional
    public void arquivarDenunciaAvaliacao(Long denunciaId, String moderadorEmail) {
        DenunciaAvaliacao denuncia = buscarDenunciaAvaliacaoAberta(denunciaId);
        resolver(denuncia, StatusDenuncia.ARQUIVADA, moderadorEmail);
        registrar(moderadorEmail, TipoAcaoModeracao.ARQUIVAR_DENUNCIA_AVALIACAO, denuncia.getAvaliacao().getAvaliadoId(),
                denunciaId);
    }

    /** O texto some da lista pública (nota e tags ficam); todas as denúncias abertas dela procedem. */
    @Transactional
    public void removerComentarioAvaliacao(Long denunciaId, String moderadorEmail) {
        DenunciaAvaliacao denuncia = buscarDenunciaAvaliacaoAberta(denunciaId);
        Avaliacao avaliacao = denuncia.getAvaliacao();
        avaliacao.setComentarioRemovido(true);
        resolver(denuncia, StatusDenuncia.PROCEDENTE, moderadorEmail);
        denunciaAvaliacaoRepository.findByAvaliacao_IdAndStatus(avaliacao.getId(), StatusDenuncia.ABERTA)
                .forEach(outra -> resolver(outra, StatusDenuncia.PROCEDENTE, moderadorEmail));
        registrar(moderadorEmail, TipoAcaoModeracao.REMOVER_COMENTARIO_AVALIACAO, avaliacao.getAvaliadoId(), denunciaId);
    }

    /** Comentários de chefs sobre clientes: privados, só a moderação lê. */
    @Transactional(readOnly = true)
    public Page<ComentarioClienteModeracaoDto> listarComentariosDeClientes(Pageable pageable) {
        Page<Avaliacao> pagina = avaliacaoRepository.findByAvaliadoTipoAndComentarioIsNotNullOrderByCriadaEmDescIdDesc(
                TipoUsuario.CLIENTE, pageable);
        Map<UUID, String> nomes = nomes(pagina.stream().flatMap(a -> Stream.of(
                Map.entry(a.getAutorId(), a.getAutorTipo()), Map.entry(a.getAvaliadoId(), a.getAvaliadoTipo()))));
        return pagina.map(a -> ComentarioClienteModeracaoDto.de(a, nomes));
    }

    /** Nomes das contas de uma página numa consulta por tabela (sem uma por linha). */
    private Map<UUID, String> nomes(Stream<Map.Entry<UUID, TipoUsuario>> contas) {
        Map<TipoUsuario, Set<UUID>> porTipo = contas.collect(Collectors.groupingBy(Map.Entry::getValue,
                Collectors.mapping(Map.Entry::getKey, Collectors.toSet())));
        Map<UUID, String> nomes = new HashMap<>();
        usuarioRepository.findAllById(porTipo.getOrDefault(TipoUsuario.CLIENTE, Set.of()))
                .forEach(u -> nomes.put(u.getId(), u.getNome()));
        chefeRepository.findAllById(porTipo.getOrDefault(TipoUsuario.CHEFE, Set.of()))
                .forEach(c -> nomes.put(c.getId(), c.getNome()));
        return nomes;
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

    private void resolver(DenunciaAvaliacao denuncia, StatusDenuncia status, String moderadorEmail) {
        denuncia.setStatus(status);
        denuncia.setResolvidaEm(LocalDateTime.now(clock));
        denuncia.setResolvidaPor(moderadorEmail);
    }

    private DenunciaAvaliacao buscarDenunciaAvaliacaoAberta(Long denunciaId) {
        DenunciaAvaliacao denuncia = denunciaAvaliacaoRepository.findById(denunciaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("denúncia não encontrada"));
        if (denuncia.getStatus() != StatusDenuncia.ABERTA) {
            throw new ConflitoException("denúncia já resolvida");
        }
        return denuncia;
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
