package com.couto.chefe_api.Services;

import com.couto.chefe_api.Services.eventos.NotificacoesAlteradasEvent;
import com.couto.chefe_api.domin.Notificacao;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.NotificacaoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificacaoServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2020-01-15T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private final NotificacaoRepository repository = mock(NotificacaoRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final NotificacaoService service = new NotificacaoService(repository, eventPublisher, RELOGIO);
    private final UUID bia = UUID.randomUUID();

    @Test
    void criadaEmVemDoRelogio() {
        service.notificar(bia, TipoUsuario.CHEFE, EventoNotificacao.AGENDAMENTO_CRIADO, "oi", "1");

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCriadaEm()).isEqualTo(LocalDateTime.now(RELOGIO));
    }

    @Test
    void apagarDasReferenciasAvisaCadaAfetado() {
        UUID moderador = UUID.randomUUID();
        Set<EventoNotificacao> eventos = Set.of(EventoNotificacao.MENSAGEM_RECEBIDA);
        when(repository.destinatariosDasReferencias(eventos, List.of("7"))).thenReturn(List.of(bia, moderador));
        when(repository.countByDestinatarioIdAndLidaFalse(bia)).thenReturn(2L);

        service.apagarDasReferencias(eventos, List.of("7"));

        verify(repository).apagarDasReferencias(eventos, List.of("7"));
        verify(eventPublisher).publishEvent(new NotificacoesAlteradasEvent(bia, 2));
        verify(eventPublisher).publishEvent(new NotificacoesAlteradasEvent(moderador, 0));
    }

    @Test
    void semReferenciasNaoConsultaNemAvisa() {
        service.apagarDasReferencias(Set.of(EventoNotificacao.MENSAGEM_RECEBIDA), List.of());

        verify(repository, never()).destinatariosDasReferencias(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
