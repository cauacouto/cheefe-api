package com.couto.chefe_api.service;

import com.couto.chefe_api.dto.NotificacaoDto;
import com.couto.chefe_api.exception.RecursoNaoEncontradoException;
import com.couto.chefe_api.domain.Notificacao;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.NotificacaoRepository;
import com.couto.chefe_api.service.eventos.NotificacoesAlteradasEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificacaoService {

    private final NotificacaoRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

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
        notificacao.setCriadaEm(LocalDateTime.now(clock));
        repository.save(notificacao);
        avisarContagem(destinatarioId);
    }

    /** Sem aviso: só é usado quando a conta do próprio destinatário está sendo apagada. */
    @Transactional
    public void apagarDoDestinatario(UUID destinatarioId) {
        repository.apagarDoDestinatario(destinatarioId);
    }

    /** Apaga as notificações que apontam para esses registros e atualiza o sino de quem as tinha. */
    @Transactional
    public void apagarDasReferencias(Collection<EventoNotificacao> eventos, Collection<String> referencias) {
        if (referencias.isEmpty()) {
            return;
        }
        List<UUID> afetados = repository.destinatariosDasReferencias(eventos, referencias);
        repository.apagarDasReferencias(eventos, referencias);
        afetados.forEach(this::avisarContagem);
    }

    /** O sino de todas as abas acompanha: a contagem sai pelo SSE depois do commit. */
    private void avisarContagem(UUID destinatarioId) {
        eventPublisher.publishEvent(new NotificacoesAlteradasEvent(destinatarioId,
                repository.countByDestinatarioIdAndLidaFalse(destinatarioId)));
    }
}
