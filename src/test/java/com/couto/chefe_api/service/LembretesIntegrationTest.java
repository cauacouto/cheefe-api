package com.couto.chefe_api.service;

import com.couto.chefe_api.MenuDeTeste;
import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.Notificacao;
import com.couto.chefe_api.domain.RestricaoAgendamento;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.OcasiaoMenu;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoRestricao;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.NotificacaoRepository;
import com.couto.chefe_api.repository.PratosRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Lembrete ao cliente 3 dias antes e resumo ao chef 1 dia antes, na rotina de 15 min (relógio fixo). */
@SpringBootTest
@Import(LembretesIntegrationTest.RelogioFixo.class)
@ActiveProfiles("test")
class LembretesIntegrationTest {

    /** Meio-dia: "hoje" e "amanhã" não dependem da hora em que o teste roda. */
    static final LocalDateTime AGORA = LocalDateTime.now().plusDays(1).withHour(12).withMinute(0).withSecond(0).withNano(0);
    static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        @Primary
        Clock relogioFixo() {
            ZoneId zona = ZoneId.systemDefault();
            return Clock.fixed(AGORA.atZone(zona).toInstant(), zona);
        }
    }

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired PratosRepository pratosRepository;
    @Autowired MenuDeTeste menus;
    @Autowired AgendamentoRotina rotina;
    @MockitoSpyBean AgendamentoService agendamentoService;

    ChefeModel bia;
    UserModel ana;

    @BeforeEach
    void setUp() {
        bia = novoChefe("Bia");
        ana = novoCliente("Ana");
    }

    @AfterEach
    void limparBanco() {
        notificacaoRepository.deleteAll();
        agendamentoRepository.deleteAll();
        pratosRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void lembreteDoClienteNaJanelaDe72h() {
        Agendamento naBorda = agendamento(AGORA.plusHours(72), StatusAgendamento.CONFIRMADO);
        Agendamento umMinutoDepois = agendamento(AGORA.plusHours(72).plusMinutes(1), StatusAgendamento.CONFIRMADO);

        rotina.processar();

        LocalDateTime inicio = naBorda.getDataHoraInicial();
        LocalDateTime prazo = inicio.minusHours(48);
        assertThat(notificacoes(EventoNotificacao.LEMBRETE_DETALHES)).singleElement().satisfies(n -> {
            assertThat(n.getDestinatarioId()).isEqualTo(ana.getId());
            assertThat(n.getReferenciaId()).isEqualTo(String.valueOf(naBorda.getId()));
            assertThat(n.getMensagem()).isEqualTo("Seu agendamento de " + inicio.format(DIA) + " às " + inicio.format(HORA)
                    + " é em 3 dias: confirme as restrições e observações até " + prazo.format(DIA) + " às " + prazo.format(HORA));
        });
        assertThat(recarregar(naBorda).getLembreteClienteEm()).isEqualTo(AGORA);
        assertThat(recarregar(umMinutoDepois).getLembreteClienteEm()).isNull();
    }

    @Test
    void lembreteUmaVezSo() {
        agendamento(AGORA.plusHours(60), StatusAgendamento.CONFIRMADO);

        rotina.processar();
        rotina.processar();

        assertThat(notificacoes(EventoNotificacao.LEMBRETE_DETALHES)).hasSize(1);
    }

    @Test
    void confirmadoComMenosDe48hPulaOLembreteDoCliente() {
        Agendamento tarde = agendamento(AGORA.plusHours(48), StatusAgendamento.CONFIRMADO);
        Agendamento muitoTarde = agendamento(AGORA.plusHours(30), StatusAgendamento.CONFIRMADO);

        rotina.processar();

        assertThat(notificacoes(EventoNotificacao.LEMBRETE_DETALHES)).isEmpty();
        assertThat(recarregar(tarde).getLembreteClienteEm()).isNull();
        assertThat(recarregar(muitoTarde).getLembreteClienteEm()).isNull();
    }

    @Test
    void resumoDoChefNaJanelaDe24h() {
        Agendamento dentro = agendamento(AGORA.plusHours(24), StatusAgendamento.CONFIRMADO,
                new RestricaoAgendamento(TipoRestricao.VEGETARIANO, 1), new RestricaoAgendamento(TipoRestricao.SEM_GLUTEN, 2));
        Agendamento fora = agendamento(AGORA.plusHours(24).plusMinutes(1), StatusAgendamento.CONFIRMADO);

        rotina.processar();

        assertThat(notificacoes(EventoNotificacao.RESUMO_DO_SERVICO)).singleElement().satisfies(n -> {
            assertThat(n.getDestinatarioId()).isEqualTo(bia.getId());
            assertThat(n.getReferenciaId()).isEqualTo(String.valueOf(dentro.getId()));
            assertThat(n.getMensagem()).isEqualTo("Amanhã às " + dentro.getDataHoraInicial().format(HORA) + ": "
                    + dentro.getMenuNome() + " para 2 pessoas, 2 restrições");
        });
        assertThat(recarregar(dentro).getResumoChefEm()).isEqualTo(AGORA);
        assertThat(recarregar(fora).getResumoChefEm()).isNull();
    }

    @Test
    void resumoHojeAsEAmanhaAs() {
        Agendamento hoje = agendamento(AGORA.plusHours(5), StatusAgendamento.CONFIRMADO,
                new RestricaoAgendamento(TipoRestricao.VEGETARIANO, 1));
        Agendamento amanha = agendamento(AGORA.plusHours(20), StatusAgendamento.CONFIRMADO);

        rotina.processar();

        assertThat(notificacoes(EventoNotificacao.RESUMO_DO_SERVICO)).extracting(Notificacao::getMensagem)
                .containsExactlyInAnyOrder(
                        "Hoje às " + hoje.getDataHoraInicial().format(HORA) + ": " + hoje.getMenuNome() + " para 2 pessoas, 1 restrição",
                        "Amanhã às " + amanha.getDataHoraInicial().format(HORA) + ": " + amanha.getMenuNome() + " para 2 pessoas, 0 restrições");
    }

    @Test
    void resumoComUmaPessoaFalaNoSingular() {
        Agendamento sozinho = agendamento(AGORA.plusHours(5), StatusAgendamento.CONFIRMADO);
        sozinho.setQuantidadePessoas(1);
        agendamentoRepository.save(sozinho);

        rotina.processar();

        assertThat(notificacoes(EventoNotificacao.RESUMO_DO_SERVICO)).singleElement().satisfies(n ->
                assertThat(n.getMensagem()).isEqualTo("Hoje às " + sozinho.getDataHoraInicial().format(HORA) + ": "
                        + sozinho.getMenuNome() + " para 1 pessoa, 0 restrições"));
    }

    @Test
    void resumoUmaVezSo() {
        agendamento(AGORA.plusHours(10), StatusAgendamento.CONFIRMADO);

        rotina.processar();
        rotina.processar();

        assertThat(notificacoes(EventoNotificacao.RESUMO_DO_SERVICO)).hasSize(1);
    }

    @Test
    void pendenteNaoRecebeLembrete() {
        Agendamento em3Dias = agendamento(AGORA.plusHours(60), StatusAgendamento.PENDENTE);
        Agendamento em1Dia = agendamento(AGORA.plusHours(10), StatusAgendamento.PENDENTE);

        rotina.processar();

        assertThat(notificacaoRepository.findAll()).isEmpty();
        assertThat(recarregar(em3Dias).getLembreteClienteEm()).isNull();
        assertThat(recarregar(em1Dia).getResumoChefEm()).isNull();
    }

    @Test
    void canceladoDepoisDeSelecionadoNaoRecebe() {
        Agendamento agendamento = agendamento(AGORA.plusHours(60), StatusAgendamento.CONFIRMADO);
        Agendamento resumo = agendamento(AGORA.plusHours(10), StatusAgendamento.CONFIRMADO);
        // o cliente cancela entre a seleção dos ids e a vez do agendamento na rotina
        Mockito.doAnswer(invocacao -> {
            agendamentoService.cancelarAgendamento(agendamento.getId(), ana.getId(), null);
            return invocacao.callRealMethod();
        }).when(agendamentoService).lembrarCliente(agendamento.getId());
        Mockito.doAnswer(invocacao -> {
            resumo.setStatus(StatusAgendamento.CANCELADO);
            agendamentoRepository.save(resumo);
            return invocacao.callRealMethod();
        }).when(agendamentoService).resumirAoChef(resumo.getId());

        rotina.processar();

        assertThat(recarregar(agendamento).getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(recarregar(agendamento).getLembreteClienteEm()).isNull();
        assertThat(recarregar(resumo).getResumoChefEm()).isNull();
        assertThat(notificacoes(EventoNotificacao.LEMBRETE_DETALHES)).isEmpty();
        assertThat(notificacoes(EventoNotificacao.RESUMO_DO_SERVICO)).isEmpty();
    }

    @Test
    void resumoDeAgendamentoAntigoSemMenu() {
        Agendamento antigo = agendamento(AGORA.plusHours(10), StatusAgendamento.CONFIRMADO);
        antigo.setMenu(null);
        antigo.setMenuNome(null);
        agendamentoRepository.save(antigo);

        rotina.processar();

        assertThat(notificacoes(EventoNotificacao.RESUMO_DO_SERVICO)).singleElement().satisfies(n ->
                assertThat(n.getMensagem()).isEqualTo("Hoje às " + antigo.getDataHoraInicial().format(HORA)
                        + ": seu agendamento para 2 pessoas, 0 restrições"));
    }

    @Test
    void umIdQueFalhaNaoImpedeOsDemais() {
        Agendamento quebra = agendamento(AGORA.plusHours(60), StatusAgendamento.CONFIRMADO);
        Agendamento segue = agendamento(AGORA.plusHours(61), StatusAgendamento.CONFIRMADO);
        Agendamento resumoQuebra = agendamento(AGORA.plusHours(8), StatusAgendamento.CONFIRMADO);
        Agendamento resumoSegue = agendamento(AGORA.plusHours(9), StatusAgendamento.CONFIRMADO);
        Mockito.doThrow(new IllegalStateException("falha simulada")).when(agendamentoService).lembrarCliente(quebra.getId());
        Mockito.doThrow(new IllegalStateException("falha simulada")).when(agendamentoService).resumirAoChef(resumoQuebra.getId());

        rotina.processar();

        assertThat(recarregar(quebra).getLembreteClienteEm()).isNull();
        assertThat(recarregar(segue).getLembreteClienteEm()).isEqualTo(AGORA);
        assertThat(recarregar(resumoQuebra).getResumoChefEm()).isNull();
        assertThat(recarregar(resumoSegue).getResumoChefEm()).isEqualTo(AGORA);
    }

    // ---------------------------------------------------------------------

    private List<Notificacao> notificacoes(EventoNotificacao evento) {
        return notificacaoRepository.findAll().stream().filter(n -> n.getEvento() == evento).toList();
    }

    private Agendamento recarregar(Agendamento agendamento) {
        return agendamentoRepository.findById(agendamento.getId()).orElseThrow();
    }

    /** Agendamento de 2 pessoas pelo menu da Bia. */
    private Agendamento agendamento(LocalDateTime inicio, StatusAgendamento status, RestricaoAgendamento... restricoes) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(ana);
        agendamento.setEndereco(novoEndereco(ana));
        agendamento.setChefe(bia);
        agendamento.setStatus(status);
        agendamento.setOcasiao(OcasiaoMenu.FAMILIA);
        agendamento.setDataHoraInicial(inicio);
        agendamento.aplicarMenu(menus.ativo(bia), 2);
        agendamento.getRestricoes().addAll(Set.of(restricoes));
        return agendamentoRepository.save(agendamento);
    }

    private EnderecoModel novoEndereco(UserModel dono) {
        EnderecoModel endereco = new EnderecoModel();
        endereco.setRua("Rua A");
        endereco.setNumero("1");
        endereco.setBairro("Centro");
        endereco.setCidade("São Paulo");
        endereco.setEstado("SP");
        endereco.setCep("01001-000");
        endereco.setTipoDeResidencia(TipoDeResidencia.CASA);
        endereco.setUsuario(dono);
        return enderecoRepository.save(endereco);
    }

    private UserModel novoCliente(String nome) {
        UserModel usuario = new UserModel();
        usuario.setNome(nome);
        usuario.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        usuario.setEndereco(new ArrayList<>());
        return usuarioRepository.save(TelefoneDeTeste.verificado(usuario));
    }

    private ChefeModel novoChefe(String nome) {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome(nome);
        chefe.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setEspecialidades(Set.of(Especialidades.BRASILEIRA));
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        return chefeRepository.save(TelefoneDeTeste.verificado(chefe));
    }
}
