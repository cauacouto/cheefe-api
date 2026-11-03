package com.couto.chefe_api.service;

import com.couto.chefe_api.dto.DenunciaCriadaDto;
import com.couto.chefe_api.dto.DenunciaRequestDto;
import com.couto.chefe_api.exception.ConflitoException;
import com.couto.chefe_api.exception.RecursoNaoEncontradoException;
import com.couto.chefe_api.security.ModeracaoRegistry;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Denuncia;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.MotivoDenuncia;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.DenunciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DenunciaServiceTest {

    private final AgendamentoRepository agendamentoRepository = mock(AgendamentoRepository.class);
    private final DenunciaRepository denunciaRepository = mock(DenunciaRepository.class);
    private final NotificacaoService notificacaoService = mock(NotificacaoService.class);
    private final ModeracaoRegistry moderacao = new ModeracaoRegistry("m1@x.com,m2@x.com");
    // 2020: pelo relógio do sistema tudo aqui já passou; só o relógio injetado decide o que é futuro
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2020-01-15T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final LocalDateTime AGORA = LocalDateTime.now(RELOGIO);
    private final DenunciaService service = new DenunciaService(agendamentoRepository, denunciaRepository, notificacaoService,
            moderacao, RELOGIO);

    private final UUID clienteId = UUID.randomUUID();
    private final ChefeModel chefe = new ChefeModel();
    private final Agendamento agendamento = new Agendamento();

    @BeforeEach
    void setUp() {
        UserModel cliente = new UserModel();
        cliente.setId(clienteId);
        chefe.setId(UUID.randomUUID());
        chefe.setNome("Bia");
        agendamento.setId(7L);
        agendamento.setUsuario(cliente);
        agendamento.setChefe(chefe);
        agendamento.setStatus(StatusAgendamento.CONFIRMADO);
        agendamento.setDataHoraInicial(AGORA.minusDays(1));
        when(agendamentoRepository.findByIdAndUsuario_Id(7L, clienteId)).thenReturn(Optional.of(agendamento));
        when(denunciaRepository.save(any(Denuncia.class))).thenAnswer(inv -> {
            Denuncia denuncia = inv.getArgument(0);
            denuncia.setId(99L);
            return denuncia;
        });
        when(denunciaRepository.countDistinctClienteByChefeIdAndStatus(chefe.getId(), StatusDenuncia.ABERTA)).thenReturn(1L);
    }

    @Test
    void elegivelCriaAberta() {
        DenunciaCriadaDto criada = service.criar(dto(), clienteId);

        assertThat(criada).isEqualTo(new DenunciaCriadaDto(99L, StatusDenuncia.ABERTA));
    }

    @Test
    void agendamentoDeOutroClienteDa404() {
        assertThatThrownBy(() -> service.criar(dto(), UUID.randomUUID())).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void agendamentoPendenteDa404() {
        agendamento.setStatus(StatusAgendamento.PENDENTE);

        assertThatThrownBy(() -> service.criar(dto(), clienteId)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void agendamentoFuturoDa404() {
        agendamento.setDataHoraInicial(AGORA.plusDays(1));

        assertThatThrownBy(() -> service.criar(dto(), clienteId)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void chefForaDoAgendamentoDa404() {
        DenunciaRequestDto outroChef = new DenunciaRequestDto(7L, UUID.randomUUID(), MotivoDenuncia.HIGIENE, "sujo");

        assertThatThrownBy(() -> service.criar(outroChef, clienteId)).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(denunciaRepository, never()).save(any());
    }

    @Test
    void duplicadaDa409() {
        when(denunciaRepository.existsByCliente_IdAndChefe_IdAndStatus(clienteId, chefe.getId(), StatusDenuncia.ABERTA)).thenReturn(true);

        assertThatThrownBy(() -> service.criar(dto(), clienteId)).isInstanceOf(ConflitoException.class);
        verify(denunciaRepository, never()).save(any());
    }

    @Test
    void notificaCadaModerador() {
        service.criar(dto(), clienteId);

        for (UUID moderador : moderacao.ids()) {
            verify(notificacaoService).notificar(moderador, TipoUsuario.MODERADOR, EventoNotificacao.DENUNCIA_CRIADA,
                    "Nova denúncia contra o chef Bia (HIGIENE)", "99");
        }
    }

    @Test
    void terceiroDenuncianteOcultaENotificaUmaVez() {
        when(denunciaRepository.countDistinctClienteByChefeIdAndStatus(chefe.getId(), StatusDenuncia.ABERTA)).thenReturn(3L);

        service.criar(dto(), clienteId);

        verify(notificacaoService).notificar(chefe.getId(), TipoUsuario.CHEFE, EventoNotificacao.CHEF_OCULTO,
                "Seu perfil foi ocultado da busca enquanto denúncias são analisadas", chefe.getId().toString());
        verify(notificacaoService, times(2)).notificar(any(), eq(TipoUsuario.MODERADOR), eq(EventoNotificacao.CHEF_OCULTO),
                eq("Chef Bia foi ocultado automaticamente (3 denúncias)"), eq(chefe.getId().toString()));
    }

    @Test
    void quartoDenuncianteNaoRepeteOAviso() {
        when(denunciaRepository.countDistinctClienteByChefeIdAndStatus(chefe.getId(), StatusDenuncia.ABERTA)).thenReturn(4L);

        service.criar(dto(), clienteId);

        verify(notificacaoService, never()).notificar(any(), any(), eq(EventoNotificacao.CHEF_OCULTO), anyString(), anyString());
    }

    private DenunciaRequestDto dto() {
        return new DenunciaRequestDto(7L, chefe.getId(), MotivoDenuncia.HIGIENE, "cozinha suja");
    }

    @Test
    void criadaEmVemDoRelogio() {
        service.criar(dto(), clienteId);

        ArgumentCaptor<Denuncia> captor = ArgumentCaptor.forClass(Denuncia.class);
        verify(denunciaRepository).save(captor.capture());
        assertThat(captor.getValue().getCriadaEm()).isEqualTo(AGORA);
    }
}
