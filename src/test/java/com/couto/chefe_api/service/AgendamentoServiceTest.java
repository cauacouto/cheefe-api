package com.couto.chefe_api.service;

import java.time.Clock;
import org.mockito.Spy;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.dto.AgendamentoRequestDto;
import com.couto.chefe_api.dto.AtualizarAgendamentoRequestDto;
import com.couto.chefe_api.exception.EnderecoException;
import com.couto.chefe_api.exception.AgendamentoException;
import com.couto.chefe_api.exception.AgendamentoNaoConfirmavelException;
import com.couto.chefe_api.exception.AgendamentoNaoEditavelException;
import com.couto.chefe_api.exception.ConteudoBloqueadoException;
import com.couto.chefe_api.exception.MenuIndisponivelException;
import com.couto.chefe_api.exception.MenuNaoEncontradoException;
import com.couto.chefe_api.domain.Menu;
import com.couto.chefe_api.enums.OcasiaoMenu;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.MenuRepository;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.mapper.AgendamentoMapper;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.enums.StatusAgendamento;
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
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendamentoServiceTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UsuarioAutenticado CLIENTE = new UsuarioAutenticado(USUARIO_ID, "ana@x.com", TipoUsuario.CLIENTE);
    private static final Long MENU_ID = 12L;
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
    private MenuRepository menuRepository;
    @Mock
    private ModeracaoConteudoService moderacaoConteudo;
    @Mock
    private HistoricoAgendamentoService historico;
    @Mock
    private AgendaService agendaService;
    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private AgendamentoService service;

    private final UserModel usuario = new UserModel();
    private final EnderecoModel endereco = new EnderecoModel();
    private final ChefeModel chefeA = chefe("Bia");
    private final ChefeModel chefeB = chefe("Caio");
    private final Menu menu = menu(chefeA);

    @BeforeEach
    void setUp() {
        usuario.setId(USUARIO_ID);
        endereco.setId(1L);
        // a mesma cidade dos chefs de teste: o chef só atende na cidade de origem
        endereco.setCidade("São Paulo");
        endereco.setEstado("SP");
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(enderecoRepository.findByIdAndUsuario_Id(1L, USUARIO_ID)).thenReturn(Optional.of(endereco));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));
        when(menuRepository.findById(MENU_ID)).thenReturn(Optional.of(menu));
        when(chefeRepository.visivel(chefeA.getId())).thenReturn(true);
        when(moderacaoConteudo.verificar(any(), any())).thenAnswer(inv -> new ConteudoVerificado(inv.getArgument(0), false));
    }

    @Nested
    class Criar {

        @Test
        void gravaACopiaDoMenuOFimEOValor() {
            service.criarAgendamento(dto(DIA.withHour(18), 1L, 4), CLIENTE);

            Agendamento salvo = agendamentoSalvo();
            assertThat(salvo.getValorTotal()).isEqualByComparingTo("400.00");
            assertThat(salvo.getDataHoraFinal()).isEqualTo(DIA.withHour(20));
            assertThat(salvo.getChefe()).isSameAs(chefeA);
            assertThat(salvo.getMenu()).isSameAs(menu);
            assertThat(salvo.getMenuNome()).isEqualTo("Menu de teste");
            assertThat(salvo.getPrecoPorPessoa()).isEqualByComparingTo("100.00");
            assertThat(salvo.getEndereco()).isSameAs(endereco);
            assertThat(salvo.getUsuario()).isSameAs(usuario);
        }

        @Test
        void menuInexistenteDa404() {
            when(menuRepository.findById(MENU_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.criarAgendamento(dto(DIA.withHour(18), 1L, 4), CLIENTE))
                    .isInstanceOf(MenuNaoEncontradoException.class);
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        void chefeNaoVisivelDa409() {
            when(chefeRepository.visivel(chefeA.getId())).thenReturn(false);

            assertThatThrownBy(() -> service.criarAgendamento(dto(DIA.withHour(18), 1L, 4), CLIENTE))
                    .isInstanceOf(MenuIndisponivelException.class);
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        void moderacaoRodaAntesDeQualquerGravacao() {
            AgendamentoRequestDto pedido = dto(DIA.withHour(18), 1L, 4);
            pedido.setObservacoes("sem coentro");
            when(moderacaoConteudo.verificar("sem coentro", CLIENTE)).thenThrow(new ConteudoBloqueadoException());

            assertThatThrownBy(() -> service.criarAgendamento(pedido, CLIENTE))
                    .isInstanceOf(ConteudoBloqueadoException.class);
            verify(usuarioRepository, never()).findById(any());
            verify(agendamentoRepository, never()).save(any());
        }
    }

    @Test
    void criarComEnderecoDeOutroUsuarioDa404() {
        when(enderecoRepository.findByIdAndUsuario_Id(2L, USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarAgendamento(dto(DIA.withHour(18), 2L, 2), CLIENTE))
                .isInstanceOf(EnderecoException.class);
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void atualizarAgendamentoDeOutroUsuarioDa404() {
        when(agendamentoRepository.travarPorId(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizarAgendamento(atualizacao(null, null, 5), 8L, CLIENTE))
                .isInstanceOf(AgendamentoException.class);
    }

    @Test
    void criarPublicaEventoDeCriacao() {
        service.criarAgendamento(dto(DIA.withHour(18), 1L, 2), CLIENTE);

        verify(publisher).publishEvent(any(AgendamentoCriadoEvent.class));
    }

    @Test
    void criadoEmVemDoRelogio() {
        java.time.Instant fixo = java.time.Instant.parse("2026-10-21T15:00:00Z");
        org.mockito.Mockito.doReturn(fixo).when(clock).instant();

        service.criarAgendamento(dto(DIA.withHour(18), 1L, 2), CLIENTE);

        org.mockito.ArgumentCaptor<Agendamento> salvo = org.mockito.ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository).save(salvo.capture());
        assertThat(salvo.getValue().getCriadoEm()).isEqualTo(LocalDateTime.ofInstant(fixo, clock.getZone()));
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
            agenda.setChefe(chefeA);
            agenda.setDataHoraInicial(DIA.withHour(18));
            agenda.aplicarMenu(menu, 2);
            when(agendamentoRepository.travarPorId(7L)).thenReturn(Optional.of(agenda));
        }

        @Test
        void semEnderecoIdMantemEnderecoSemBuscar() {
            service.atualizarAgendamento(atualizacao(null, null, 5), 7L, CLIENTE);

            assertThat(agendamentoSalvo().getEndereco()).isSameAs(endereco);
            verify(enderecoRepository, never()).findByIdAndUsuario_Id(any(), any());
        }

        @Test
        void atualizaQuantidadeDePessoasERecalculaValor() {
            service.atualizarAgendamento(atualizacao(null, null, 5), 7L, CLIENTE);

            Agendamento salvo = agendamentoSalvo();
            assertThat(salvo.getQuantidadePessoas()).isEqualTo(5);
            assertThat(salvo.getValorTotal()).isEqualByComparingTo("500.00");
        }

        @Test
        void novoHorarioMantemADuracaoDaCopia() {
            service.atualizarAgendamento(atualizacao(DIA.withHour(19), null, null), 7L, CLIENTE);

            Agendamento salvo = agendamentoSalvo();
            assertThat(salvo.getDataHoraInicial()).isEqualTo(DIA.withHour(19));
            assertThat(salvo.getDataHoraFinal()).isEqualTo(DIA.withHour(21));
            assertThat(salvo.getValorTotal()).isEqualByComparingTo("200.00");
        }

        @Test
        void atualizarComEnderecoDeOutroUsuarioDa404() {
            when(enderecoRepository.findByIdAndUsuario_Id(2L, USUARIO_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.atualizarAgendamento(atualizacao(null, 2L, null), 7L, CLIENTE))
                    .isInstanceOf(EnderecoException.class);
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        void editarPublicaEventoDeEdicao() {
            service.atualizarAgendamento(atualizacao(null, null, 5), 7L, CLIENTE);

            verify(publisher).publishEvent(new AgendamentoEditadoEvent(7L));
        }

        @Test
        void agendamentoForaDePendenteNaoPodeSerEditado() {
            agenda.setStatus(StatusAgendamento.CONFIRMADO);

            assertThatThrownBy(() -> service.atualizarAgendamento(atualizacao(null, null, 5), 7L, CLIENTE))
                    .isInstanceOf(AgendamentoNaoEditavelException.class);
            verify(agendamentoRepository, never()).save(any());
        }
    }

    @Nested
    class Confirmar {

        private Agendamento agenda;

        @BeforeEach
        void agendaDoChefB() {
            agenda = new Agendamento();
            agenda.setId(9L);
            agenda.setChefe(chefeB);
            agenda.setEndereco(endereco);
            when(agendamentoRepository.travarPorId(9L)).thenReturn(Optional.of(agenda));
        }

        @Test
        void confirmarPeloChefDoAgendamento() {
            service.confirmarAgendamento(9L, chefeB.getId());

            assertThat(agenda.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
            verify(publisher).publishEvent(new AgendamentoConfirmadoEvent(9L, chefeB.getId()));
        }

        @Test
        void confirmarPorOutroChefDa404() {
            assertThatThrownBy(() -> service.confirmarAgendamento(9L, chefeA.getId()))
                    .isInstanceOf(AgendamentoException.class);
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        void confirmarJaConfirmadoDa409() {
            agenda.setStatus(StatusAgendamento.CONFIRMADO);

            assertThatThrownBy(() -> service.confirmarAgendamento(9L, chefeB.getId()))
                    .isInstanceOf(AgendamentoNaoConfirmavelException.class)
                    .hasMessage("Só é possível confirmar agendamentos pendentes (status atual: CONFIRMADO)");
            verify(agendamentoRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------

    private static ChefeModel chefe(String nome) {
        ChefeModel chefe = new ChefeModel();
        chefe.setId(UUID.randomUUID());
        chefe.setNome(nome);
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setEspecialidades(Set.of(Especialidades.BRASILEIRA));
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        return com.couto.chefe_api.TelefoneDeTeste.verificado(chefe);
    }

    private static Menu menu(ChefeModel chefe) {
        Menu menu = new Menu();
        menu.setId(MENU_ID);
        menu.setChefe(chefe);
        menu.setNome("Menu de teste");
        menu.setPrecoPorPessoa(new BigDecimal("100.00"));
        menu.setMinimoConvidados(1);
        menu.setMaximoConvidados(50);
        menu.setDuracaoMinutos(120);
        menu.setIngredientesInclusos(true);
        return menu;
    }

    private AgendamentoRequestDto dto(LocalDateTime inicio, Long enderecoId, Integer quantidadePessoas) {
        return new AgendamentoRequestDto(MENU_ID, inicio, enderecoId, quantidadePessoas, OcasiaoMenu.FAMILIA, null, null);
    }

    private static AtualizarAgendamentoRequestDto atualizacao(LocalDateTime inicio, Long enderecoId,
                                                              Integer quantidadePessoas) {
        return new AtualizarAgendamentoRequestDto(inicio, enderecoId, quantidadePessoas, null, null, null, null);
    }

    private Agendamento agendamentoSalvo() {
        ArgumentCaptor<Agendamento> captor = ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository).save(captor.capture());
        return captor.getValue();
    }
}
