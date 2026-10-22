package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.NotificacaoDto;
import com.couto.chefe_api.Excepitons.RecursoNaoEncontradoException;
import com.couto.chefe_api.domin.Notificacao;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.NotificacaoRepository;
import com.couto.chefe_api.Services.eventos.NotificacoesAlteradasEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificacaoService {

    private final NotificacaoRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<NotificacaoDto> listar(UUID destinatarioId, boolean somenteNaoLidas, Pageable pageable) {
        Page<Notificacao> pagina = somenteNaoLidas
                ? repository.findByDestinatarioIdAndLidaFalseOrderByCriadaEmDescIdDesc(destinatarioId, pageable)
                : repository.findByDestinatarioIdOrderByCriadaEmDescIdDesc(destinatarioId, pageable);
        return pagina.map(NotificacaoDto::de);
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(UUID destinatarioId) {
        return repository.countByDestinatarioIdAndLidaFalse(destinatarioId);
    }

    /** Idempotente: marcar de novo uma notificação já lida não muda nada. */
    @Transactional
    public void marcarLida(Long id, UUID destinatarioId) {
        Notificacao notificacao = repository.findByIdAndDestinatarioId(id, destinatarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("notificação não encontrada"));
        if (!notificacao.isLida()) {
            notificacao.setLida(true);
            avisarContagem(destinatarioId);
        }
    }

    @Transactional
    public void marcarTodasLidas(UUID destinatarioId) {
        if (repository.marcarTodasLidas(destinatarioId) > 0) {
            avisarContagem(destinatarioId);
        }
    }

    @Transactional(readOnly = true)
    public boolean existeNaoLida(UUID destinatarioId, EventoNotificacao evento, String referenciaId) {
        return repository.existsByDestinatarioIdAndEventoAndReferenciaIdAndLidaFalse(destinatarioId, evento, referenciaId);
    }

    @Transactional
    public void marcarLidasDaReferencia(UUID destinatarioId, EventoNotificacao evento, String referenciaId) {
        if (repository.marcarLidasDaReferencia(destinatarioId, evento, referenciaId) > 0) {
            avisarContagem(destinatarioId);
        }
    }

    @Transactional
    public void notificar(UUID destinatarioId, TipoUsuario tipo, EventoNotificacao evento, String mensagem, String referenciaId) {
        Notificacao notificacao = new Notificacao();
        notificacao.setDestinatarioId(destinatarioId);
        notificacao.setDestinatarioTipo(tipo);
        notificacao.setEvento(evento);
        notificacao.setMensagem(mensagem);
        notificacao.setReferenciaId(referenciaId);
        repository.save(notificacao);
        avisarContagem(destinatarioId);
    }

    /** O sino de todas as abas acompanha: a contagem sai pelo SSE depois do commit. */
    private void avisarContagem(UUID destinatarioId) {
        eventPublisher.publishEvent(new NotificacoesAlteradasEvent(destinatarioId,
                repository.countByDestinatarioIdAndLidaFalse(destinatarioId)));
    }
}
