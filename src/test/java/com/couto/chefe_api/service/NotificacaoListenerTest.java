package com.couto.chefe_api.service;

import com.couto.chefe_api.enums.MotivoRecusa;
import com.couto.chefe_api.enums.MotivoCancelamento;
import com.couto.chefe_api.service.eventos.AgendamentoExpiradoEvent;
import com.couto.chefe_api.service.eventos.ChefSaiuDoAgendamentoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoCanceladoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoConfirmadoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoCriadoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoEditadoEvent;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class NotificacaoListenerTest {

    private final AgendamentoRepository agendamentoRepository = mock(AgendamentoRepository.class);
    private final NotificacaoService notificacaoService = mock(NotificacaoService.class);
    private final NotificacaoListener listener = new NotificacaoListener(agendamentoRepository, notificacaoService);

    private final ChefeModel bia = chefe("Bia");
    private final UserModel ana = new UserModel();
    private final Agendamento agendamento = new Agendamento();

    @BeforeEach
    void setUp() {
        ana.setId(UUID.randomUUID());
        ana.setNome("Ana");
        agendamento.setId(42L);
        agendamento.setUsuario(ana);
        agendamento.setChefe(bia);
        agendamento.setDataHoraInicial(LocalDateTime.of(2026, 10, 25, 18, 0));
        when(agendamentoRepository.findById(42L)).thenReturn(Optional.of(agendamento));
    }

    @Test
    void criadoNotificaOChef() {
        listener.aoCriar(new AgendamentoCriadoEvent(42L));

        String mensagem = "Ana pediu um agendamento para 25/10 às 18:00";
        verify(notificacaoService).notificar(bia.getId(), TipoUsuario.CHEFE, EventoNotificacao.AGENDAMENTO_CRIADO, mensagem, "42");
        verifyNoMoreInteractions(notificacaoService);
    }

    @Test
    void editadoNotificaOChef() {
        listener.aoEditar(new AgendamentoEditadoEvent(42L));

        String mensagem = "Ana alterou o agendamento de 25/10";
        verify(notificacaoService).notificar(bia.getId(), TipoUsuario.CHEFE, EventoNotificacao.AGENDAMENTO_EDITADO, mensagem, "42");
    }

    @Test
    void confirmadoNotificaClienteComNomeDeQuemConfirmou() {
        listener.aoConfirmar(new AgendamentoConfirmadoEvent(42L, bia.getId()));

        verify(notificacaoService).notificar(ana.getId(), TipoUsuario.CLIENTE, EventoNotificacao.AGENDAMENTO_CONFIRMADO,
                "Chef Bia confirmou seu agendamento de 25/10", "42");
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

    @Test
    void cancelamentoAvisaOChef() {
        listener.aoCancelar(new AgendamentoCanceladoEvent(42L, MotivoCancelamento.MUDANCA_DE_PLANOS, null));

        String mensagem = "Ana cancelou o agendamento de 25/10: Mudança de planos";
        verify(notificacaoService).notificar(bia.getId(), TipoUsuario.CHEFE, EventoNotificacao.AGENDAMENTO_CANCELADO, mensagem, "42");
        verifyNoMoreInteractions(notificacaoService);
    }

    @Test
    void recusaEDesistenciaAvisamOCliente() {
        listener.aoChefSair(new ChefSaiuDoAgendamentoEvent(42L, "Bia", true, null, null));
        listener.aoChefSair(new ChefSaiuDoAgendamentoEvent(42L, "Bia", false, MotivoRecusa.IMPREVISTO, null));

        verify(notificacaoService).notificar(ana.getId(), TipoUsuario.CLIENTE, EventoNotificacao.AGENDAMENTO_RECUSADO,
                "Seu pedido de 25/10 foi recusado", "42");
        verify(notificacaoService).notificar(ana.getId(), TipoUsuario.CLIENTE, EventoNotificacao.CHEF_DESISTIU,
                "O agendamento de 25/10 foi cancelado: o chef Bia desistiu: Tive um imprevisto", "42");
    }

    @Test
    void recusaComMotivoEDescricaoVaiNoFimDaMensagem() {
        listener.aoChefSair(new ChefSaiuDoAgendamentoEvent(42L, "Bia", true,
                MotivoRecusa.LOCAL_FORA_DA_AREA, "fico em Santo André"));

        verify(notificacaoService).notificar(ana.getId(), TipoUsuario.CLIENTE, EventoNotificacao.AGENDAMENTO_RECUSADO,
                "Seu pedido de 25/10 foi recusado: O local fica fora da minha área de atendimento"
                        + " — fico em Santo André", "42");
        verifyNoMoreInteractions(notificacaoService);
    }

    @Test
    void expiracao() {
        listener.aoExpirar(new AgendamentoExpiradoEvent(42L));

        verify(notificacaoService).notificar(ana.getId(), TipoUsuario.CLIENTE, EventoNotificacao.AGENDAMENTO_EXPIRADO,
                "Seu pedido de 25/10 expirou sem confirmação", "42");
    }

    @Test
    void descricaoLongaEhEncurtadaECabeNos300() {
        org.mockito.ArgumentCaptor<String> mensagem = org.mockito.ArgumentCaptor.forClass(String.class);

        listener.aoCancelar(new AgendamentoCanceladoEvent(42L, MotivoCancelamento.ENCONTREI_OUTRA_OPCAO, "x".repeat(300)));

        verify(notificacaoService).notificar(org.mockito.ArgumentMatchers.eq(bia.getId()), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), mensagem.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(mensagem.getValue()).hasSizeLessThanOrEqualTo(300)
                .startsWith("Ana cancelou o agendamento de 25/10: Encontrei outra opção — xxx")
                .endsWith("…")
                .contains(" — " + "x".repeat(120) + "…");
    }
}
