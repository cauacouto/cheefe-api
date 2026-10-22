package com.couto.chefe_api.Services;

import com.couto.chefe_api.Services.eventos.AgendamentoConfirmadoEvent;
import com.couto.chefe_api.Services.eventos.AgendamentoCriadoEvent;
import com.couto.chefe_api.Services.eventos.AgendamentoEditadoEvent;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.AgendamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class NotificacaoListenerTest {

    private final AgendamentoRepository agendamentoRepository = mock(AgendamentoRepository.class);
    private final NotificacaoService notificacaoService = mock(NotificacaoService.class);
    private final NotificacaoListener listener = new NotificacaoListener(agendamentoRepository, notificacaoService);

    private final ChefeModel bia = chefe("Bia");
    private final ChefeModel caio = chefe("Caio");
    private final UserModel ana = new UserModel();
    private final Agendamento agendamento = new Agendamento();

    @BeforeEach
    void setUp() {
        ana.setId(UUID.randomUUID());
        ana.setNome("Ana");
        agendamento.setId(42L);
        agendamento.setUsuario(ana);
        agendamento.setChefes(new ArrayList<>(List.of(bia, caio)));
        agendamento.setDataHoraInicial(LocalDateTime.of(2026, 10, 25, 18, 0));
        when(agendamentoRepository.findById(42L)).thenReturn(Optional.of(agendamento));
    }

    @Test
    void criadoNotificaCadaChef() {
        listener.aoCriar(new AgendamentoCriadoEvent(42L));

        String mensagem = "Ana pediu um agendamento para 25/10 às 18:00";
        verify(notificacaoService).notificar(bia.getId(), TipoUsuario.CHEFE, EventoNotificacao.AGENDAMENTO_CRIADO, mensagem, "42");
        verify(notificacaoService).notificar(caio.getId(), TipoUsuario.CHEFE, EventoNotificacao.AGENDAMENTO_CRIADO, mensagem, "42");
        verifyNoMoreInteractions(notificacaoService);
    }

    @Test
    void editadoNotificaCadaChef() {
        listener.aoEditar(new AgendamentoEditadoEvent(42L));

        String mensagem = "Ana alterou o agendamento de 25/10";
        verify(notificacaoService).notificar(bia.getId(), TipoUsuario.CHEFE, EventoNotificacao.AGENDAMENTO_EDITADO, mensagem, "42");
        verify(notificacaoService).notificar(caio.getId(), TipoUsuario.CHEFE, EventoNotificacao.AGENDAMENTO_EDITADO, mensagem, "42");
    }

    @Test
    void confirmadoNotificaClienteComNomeDeQuemConfirmou() {
        listener.aoConfirmar(new AgendamentoConfirmadoEvent(42L, caio.getId()));

        verify(notificacaoService).notificar(ana.getId(), TipoUsuario.CLIENTE, EventoNotificacao.AGENDAMENTO_CONFIRMADO,
                "Chef Caio confirmou seu agendamento de 25/10", "42");
        verifyNoMoreInteractions(notificacaoService);
    }

    @Test
    void agendamentoSemDataNaoQuebraEdicaoNemConfirmacao() {
        agendamento.setDataHoraInicial(null);

        listener.aoEditar(new AgendamentoEditadoEvent(42L));
        listener.aoConfirmar(new AgendamentoConfirmadoEvent(42L, bia.getId()));
        listener.aoCriar(new AgendamentoCriadoEvent(42L));

        verify(notificacaoService).notificar(bia.getId(), TipoUsuario.CHEFE, EventoNotificacao.AGENDAMENTO_EDITADO,
                "Ana alterou o agendamento de data a definir", "42");
        verify(notificacaoService).notificar(ana.getId(), TipoUsuario.CLIENTE, EventoNotificacao.AGENDAMENTO_CONFIRMADO,
                "Chef Bia confirmou seu agendamento de data a definir", "42");
        verify(notificacaoService).notificar(bia.getId(), TipoUsuario.CHEFE, EventoNotificacao.AGENDAMENTO_CRIADO,
                "Ana pediu um agendamento para data a definir", "42");
    }

    private static ChefeModel chefe(String nome) {
        ChefeModel chefe = new ChefeModel();
        chefe.setId(UUID.randomUUID());
        chefe.setNome(nome);
        return chefe;
    }
}
