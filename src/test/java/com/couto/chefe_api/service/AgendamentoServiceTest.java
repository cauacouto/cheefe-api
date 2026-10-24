package com.couto.chefe_api.service;

import java.time.Clock;
import org.mockito.Spy;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.dto.AgendamentoRequestDto;
import com.couto.chefe_api.dto.AtualizarAgendamentoRequestDto;
import com.couto.chefe_api.exception.AgendamentoInvalidoException;
import com.couto.chefe_api.exception.EnderecoException;
import com.couto.chefe_api.exception.AgendamentoException;
import com.couto.chefe_api.exception.AgendamentoNaoConfirmavelException;
import com.couto.chefe_api.exception.AgendamentoNaoEditavelException;
import com.couto.chefe_api.exception.ChefeIndisponivelException;
import com.couto.chefe_api.exception.ChefeNaoEncontradoException;
import com.couto.chefe_api.mapper.AgendamentoMapper;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.repository.DenunciaRepository;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import com.couto.chefe_api.service.eventos.AgendamentoConfirmadoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoCriadoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoEditadoEvent;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendamentoServiceTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final LocalDateTime DIA = LocalDateTime.now().plusDays(10).withHour(0).withMinute(0).withSecond(0).withNano(0);

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ChefeRepository chefeRepository;
    @Mock
    private AgendamentoMapper agendamentoMapper;
    @Mock
    private EnderecoRepository enderecoRepository;
    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private ApplicationEventPublisher publisher;
    @Mock
    private DenunciaRepository denunciaRepository;
    @Mock
    private HistoricoAgendamentoService historico;
    // telefone verificado: exigência coberta em TelefoneExigidoIntegrationTest; aqui o mock deixa passar
    @Mock
    private ExigenciaTelefone exigenciaTelefone;
    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private AgendamentoService service;

    private final UserModel usuario = new UserModel();
    private final EnderecoModel endereco = new EnderecoModel();
    private final ChefeModel chefeA = chefe("100.00");
    private final ChefeModel chefeB = chefe("50.50");

    @BeforeEach
    void setUp() {
        usuario.setId(USUARIO_ID);
        endereco.setId(1L);
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(enderecoRepository.findByIdAndUsuario_Id(1L, USUARIO_ID)).thenReturn(Optional.of(endereco));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    class Criar {

        @Test
        void periodoValidoCalculaValorPelaSomaDosChefes() {
            chefesExistentes(chefeA, chefeB);

            service.criarAgendamento(dto(DIA.withHour(18), DIA.withHour(21), 1L, ids(chefeA, chefeB), 4), USUARIO_ID);

            Agendamento salvo = agendamentoSalvo();
            assertThat(salvo.getValorTotal()).isEqualByComparingTo("451.50");
            assertThat(salvo.getChefes()).containsExactlyInAnyOrder(chefeA, chefeB);
            assertThat(salvo.getEndereco()).isSameAs(endereco);
            assertThat(salvo.getUsuario()).isSameAs(usuario);
        }

        @Test
        void fimAntesDoInicioEhRejeitado() {
            chefesExistentes(chefeA);

            assertThatThrownBy(() -> service.criarAgendamento(
                    dto(DIA.withHour(21), DIA.withHour(18), 1L, ids(chefeA), 2), USUARIO_ID))
                    .isInstanceOf(AgendamentoInvalidoException.class);
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        void fimIgualAoInicioEhRejeitado() {
            chefesExistentes(chefeA);

            assertThatThrownBy(() -> service.criarAgendamento(
                    dto(DIA.withHour(18), DIA.withHour(18), 1L, ids(chefeA), 2), USUARIO_ID))
                    .isInstanceOf(AgendamentoInvalidoException.class);
        }

        @Test
        void chefeInexistenteEhRejeitado() {
            chefesExistentes(chefeA);

            assertThatThrownBy(() -> service.criarAgendamento(
                    dto(DIA.withHour(18), DIA.withHour(21), 1L, List.of(chefeA.getId(), UUID.randomUUID()), 2), USUARIO_ID))
                    .isInstanceOf(ChefeNaoEncontradoException.class);
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        void semChefesEhRejeitado() {
            assertThatThrownBy(() -> service.criarAgendamento(
                    dto(DIA.withHour(18), DIA.withHour(21), 1L, null, 2), USUARIO_ID))
                    .isInstanceOf(AgendamentoInvalidoException.class);
            assertThatThrownBy(() -> service.criarAgendamento(
                    dto(DIA.withHour(18), DIA.withHour(21), 1L, List.of(), 2), USUARIO_ID))
                    .isInstanceOf(AgendamentoInvalidoException.class);
        }

        @Test
        void chefeInativoOuIndisponivelEhRejeitado() {
            chefeB.setAtivo(false);
            chefesExistentes(chefeA, chefeB);

            assertThatThrownBy(() -> service.criarAgendamento(
                    dto(DIA.withHour(18), DIA.withHour(21), 1L, ids(chefeA, chefeB), 2), USUARIO_ID))
                    .isInstanceOf(ChefeIndisponivelException.class);
        }

        @Test
        void idsRepetidosContamUmaVez() {
            chefesExistentes(chefeA);

            service.criarAgendamento(
                    dto(DIA.withHour(18), DIA.withHour(20), 1L, List.of(chefeA.getId(), chefeA.getId()), 2), USUARIO_ID);

            assertThat(agendamentoSalvo().getValorTotal()).isEqualByComparingTo("200.00");
        }
    }

    @Test
    void criarComEnderecoDeOutroUsuarioDa404() {
        when(enderecoRepository.findByIdAndUsuario_Id(2L, USUARIO_ID)).thenReturn(Optional.empty());
        chefesExistentes(chefeA);

        assertThatThrownBy(() -> service.criarAgendamento(
                dto(DIA.withHour(18), DIA.withHour(21), 2L, ids(chefeA), 2), USUARIO_ID))
                .isInstanceOf(EnderecoException.class);
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void atualizarAgendamentoDeOutroUsuarioDa404() {
        when(agendamentoRepository.travarPorId(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizarAgendamento(atualizacao(null, null, null, null, 5), 8L, USUARIO_ID))
                .isInstanceOf(AgendamentoException.class);
    }

    @Test
    void agendarChefIncompletoDa400() {
        chefeA.setSenioridade(null);
        chefesExistentes(chefeA);

        assertThatThrownBy(() -> service.criarAgendamento(
                dto(DIA.withHour(18), DIA.withHour(21), 1L, ids(chefeA), 2), USUARIO_ID))
                .isInstanceOf(AgendamentoInvalidoException.class)
                .hasMessage("chef com perfil incompleto");
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void criarPublicaEventoDeCriacao() {
        chefesExistentes(chefeA);

        service.criarAgendamento(dto(DIA.withHour(18), DIA.withHour(21), 1L, ids(chefeA), 2), USUARIO_ID);

        verify(publisher).publishEvent(any(AgendamentoCriadoEvent.class));
    }

    @Test
    void criadoEmVemDoRelogio() {
        chefesExistentes(chefeA);
        java.time.Instant fixo = java.time.Instant.parse("2026-10-21T15:00:00Z");
        org.mockito.Mockito.doReturn(fixo).when(clock).instant();

        service.criarAgendamento(dto(DIA.withHour(18), DIA.withHour(21), 1L, ids(chefeA), 2), USUARIO_ID);

        org.mockito.ArgumentCaptor<Agendamento> salvo = org.mockito.ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository).save(salvo.capture());
        assertThat(salvo.getValue().getCriadoEm()).isEqualTo(LocalDateTime.ofInstant(fixo, clock.getZone()));
    }

    @Test
    void agendarChefSuspensoDa409() {
        chefeA.setSuspenso(true);
        chefesExistentes(chefeA);

        assertThatThrownBy(() -> service.criarAgendamento(
                dto(DIA.withHour(18), DIA.withHour(21), 1L, ids(chefeA), 2), USUARIO_ID))
                .isInstanceOf(ChefeIndisponivelException.class);
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void agendarChefBloqueadoDa409() {
        chefeA.setBloqueadoEm(LocalDateTime.now());
        chefesExistentes(chefeA);

        assertThatThrownBy(() -> service.criarAgendamento(
                dto(DIA.withHour(18), DIA.withHour(21), 1L, ids(chefeA), 2), USUARIO_ID))
                .isInstanceOf(ChefeIndisponivelException.class);
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void agendarChefOcultoPorDenunciasDa409() {
        chefesExistentes(chefeA);
        when(denunciaRepository.countDistinctClienteByChefeIdAndStatus(chefeA.getId(), StatusDenuncia.ABERTA)).thenReturn(3L);

        assertThatThrownBy(() -> service.criarAgendamento(
                dto(DIA.withHour(18), DIA.withHour(21), 1L, ids(chefeA), 2), USUARIO_ID))
                .isInstanceOf(ChefeIndisponivelException.class);
    }

    @Nested
    class Atualizar {

        private Agendamento agenda;

        @BeforeEach
        void agendaExistente() {
            agenda = new Agendamento();
            agenda.setId(7L);
            agenda.setUsuario(usuario);
            agenda.setEndereco(endereco);
            agenda.setChefes(new ArrayList<>(List.of(chefeA)));
            agenda.setDataHoraInicial(DIA.withHour(18));
            agenda.setDataHoraFinal(DIA.withHour(20));
            agenda.setQuantidadePessoas(2);
            agenda.setValorTotal(new BigDecimal("200.00"));
            when(agendamentoRepository.travarPorId(7L)).thenReturn(Optional.of(agenda));
        }

        @Test
        void semEnderecoIdMantemEnderecoSemBuscar() {
            service.atualizarAgendamento(atualizacao(null, null, null, null, 5), 7L, USUARIO_ID);

            assertThat(agendamentoSalvo().getEndereco()).isSameAs(endereco);
            verify(enderecoRepository, never()).findByIdAndUsuario_Id(any(), any());
        }

        @Test
        void atualizaQuantidadeDePessoas() {
            service.atualizarAgendamento(atualizacao(null, null, null, null, 5), 7L, USUARIO_ID);

            assertThat(agendamentoSalvo().getQuantidadePessoas()).isEqualTo(5);
        }

        @Test
        void listaDeChefesPreenchidaTrocaChefesERecalculaValor() {
            chefesExistentes(chefeB);

            service.atualizarAgendamento(atualizacao(null, null, null, ids(chefeB), null), 7L, USUARIO_ID);

            Agendamento salvo = agendamentoSalvo();
            assertThat(salvo.getChefes()).containsExactly(chefeB);
            assertThat(salvo.getValorTotal()).isEqualByComparingTo("101.00");
        }

        @Test
        void listaDeChefesVaziaNaoRemoveOsChefes() {
            service.atualizarAgendamento(atualizacao(null, null, null, List.of(), null), 7L, USUARIO_ID);

            assertThat(agendamentoSalvo().getChefes()).containsExactly(chefeA);
            verify(chefeRepository, never()).findAllById(anyIterable());
        }

        @Test
        void chefeInexistenteNaAtualizacaoEhRejeitado() {
            chefesExistentes();

            assertThatThrownBy(() -> service.atualizarAgendamento(
                    atualizacao(null, null, null, List.of(UUID.randomUUID()), null), 7L, USUARIO_ID))
                    .isInstanceOf(ChefeNaoEncontradoException.class);
            assertThat(agenda.getChefes()).containsExactly(chefeA);
        }

        @Test
        void novasDatasSaoGravadasNaEntidadeERecalculamValor() {
            service.atualizarAgendamento(atualizacao(DIA.withHour(19), DIA.withHour(22), null, null, null), 7L, USUARIO_ID);

            Agendamento salvo = agendamentoSalvo();
            assertThat(salvo.getDataHoraInicial()).isEqualTo(DIA.withHour(19));
            assertThat(salvo.getDataHoraFinal()).isEqualTo(DIA.withHour(22));
            assertThat(salvo.getValorTotal()).isEqualByComparingTo("300.00");
        }

        @Test
        void soODataFinalUsaOInicioExistente() {
            service.atualizarAgendamento(atualizacao(null, DIA.withHour(21), null, null, null), 7L, USUARIO_ID);

            Agendamento salvo = agendamentoSalvo();
            assertThat(salvo.getDataHoraInicial()).isEqualTo(DIA.withHour(18));
            assertThat(salvo.getValorTotal()).isEqualByComparingTo("300.00");
        }

        @Test
        void fimAntesDoInicioExistenteEhRejeitado() {
            assertThatThrownBy(() -> service.atualizarAgendamento(
                    atualizacao(null, DIA.withHour(17), null, null, null), 7L, USUARIO_ID))
                    .isInstanceOf(AgendamentoInvalidoException.class);
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        void atualizarComEnderecoDeOutroUsuarioDa404() {
            when(enderecoRepository.findByIdAndUsuario_Id(2L, USUARIO_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.atualizarAgendamento(atualizacao(null, null, 2L, null, null), 7L, USUARIO_ID))
                    .isInstanceOf(EnderecoException.class);
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        void editarPublicaEventoDeEdicao() {
            service.atualizarAgendamento(atualizacao(null, null, null, null, 5), 7L, USUARIO_ID);

            verify(publisher).publishEvent(new AgendamentoEditadoEvent(7L));
        }

        @Test
        void agendamentoForaDePendenteNaoPodeSerEditado() {
            agenda.setStatus(StatusAgendamento.CONFIRMADO);

            assertThatThrownBy(() -> service.atualizarAgendamento(atualizacao(null, null, null, null, 5), 7L, USUARIO_ID))
                    .isInstanceOf(AgendamentoNaoEditavelException.class);
            verify(agendamentoRepository, never()).save(any());
        }
    }

    @Nested
    class Confirmar {

        private Agendamento agenda;

        @BeforeEach
        void agendaComDoisChefs() {
            agenda = new Agendamento();
            agenda.setId(9L);
            agenda.setChefes(new ArrayList<>(List.of(chefeA, chefeB)));
            when(agendamentoRepository.travarPorId(9L)).thenReturn(Optional.of(agenda));
        }

        @Test
        void confirmarPorQualquerChefDoAgendamento() {
            service.confirmarAgendamento(9L, chefeB.getId());

            assertThat(agenda.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
            verify(publisher).publishEvent(new AgendamentoConfirmadoEvent(9L, chefeB.getId()));
        }

        @Test
        void confirmarPorChefForaDoAgendamentoDa404() {
            assertThatThrownBy(() -> service.confirmarAgendamento(9L, UUID.randomUUID()))
                    .isInstanceOf(AgendamentoException.class);
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        void confirmarJaConfirmadoDa409() {
            agenda.setStatus(StatusAgendamento.CONFIRMADO);

            assertThatThrownBy(() -> service.confirmarAgendamento(9L, chefeA.getId()))
                    .isInstanceOf(AgendamentoNaoConfirmavelException.class)
                    .hasMessage("Só é possível confirmar agendamentos pendentes (status atual: CONFIRMADO)");
            verify(agendamentoRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------

    private static ChefeModel chefe(String valorHora) {
        ChefeModel chefe = new ChefeModel();
        chefe.setId(UUID.randomUUID());
        chefe.setNome("chefe " + valorHora);
        chefe.setValorHora(new BigDecimal(valorHora));
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        return com.couto.chefe_api.TelefoneDeTeste.verificado(chefe);
    }

    private void chefesExistentes(ChefeModel... chefes) {
        when(chefeRepository.findAllById(anyIterable())).thenReturn(new ArrayList<>(List.of(chefes)));
    }

    private static List<UUID> ids(ChefeModel... chefes) {
        return java.util.Arrays.stream(chefes).map(ChefeModel::getId).toList();
    }

    private static AgendamentoRequestDto dto(LocalDateTime inicio, LocalDateTime fim, Long enderecoId,
                                              List<UUID> chefeIds, Integer quantidadePessoas) {
        return new AgendamentoRequestDto(inicio, fim, enderecoId, chefeIds, quantidadePessoas);
    }

    private static AtualizarAgendamentoRequestDto atualizacao(LocalDateTime inicio, LocalDateTime fim, Long enderecoId,
                                                              List<UUID> chefeIds, Integer quantidadePessoas) {
        return new AtualizarAgendamentoRequestDto(inicio, fim, enderecoId, chefeIds, quantidadePessoas);
    }

    private Agendamento agendamentoSalvo() {
        ArgumentCaptor<Agendamento> captor = ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository).save(captor.capture());
        return captor.getValue();
    }
}
