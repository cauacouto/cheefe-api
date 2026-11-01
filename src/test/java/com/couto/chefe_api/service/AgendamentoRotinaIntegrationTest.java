package com.couto.chefe_api.service;

import com.couto.chefe_api.security.AuthCookies;
import com.couto.chefe_api.security.TokenService;
import com.couto.chefe_api.security.UsuarioAutenticado;

import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.Pratos;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.domain.AgendamentoHistorico;
import com.couto.chefe_api.enums.AcaoAgendamento;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.repository.AgendamentoHistoricoRepository;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.PratosRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rotina de 15 min: conclui confirmados que terminaram e expira pendentes que começaram (spec ciclo-agendamento). */
@SpringBootTest
@ActiveProfiles("test")
class AgendamentoRotinaIntegrationTest {

    static final LocalDateTime BASE = LocalDateTime.now().plusDays(10).withHour(18).withMinute(0).withSecond(0).withNano(0);
    static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired PratosRepository pratosRepository;
    @Autowired AgendamentoHistoricoRepository historicoRepository;
    @Autowired com.couto.chefe_api.repository.NotificacaoRepository notificacaoRepository;
    @Autowired AgendamentoRotina rotina;
    @MockitoSpyBean AgendamentoService agendamentoService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
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

    @Autowired List<org.springframework.scheduling.config.ScheduledTaskHolder> agendadores;

    @Test
    void nosTestesARotinaNaoRodaSozinha() {
        // ela mexeria em agendamentos de outros testes no meio da execução (CI intermitente)
        assertThat(agendadores.stream().flatMap(h -> h.getScheduledTasks().stream()).map(Object::toString))
                .doesNotContain(AgendamentoRotina.class.getName() + ".processar");
    }

    @Test
    void concluiOsConfirmadosQueJaTerminaramEExpiraOsPendentesQueJaComecaram() {
        LocalDateTime agora = LocalDateTime.now();
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        Agendamento terminou = novoAgendamento(ana, agora.minusHours(3), bia);
        confirmar(terminou);
        Agendamento emAndamento = novoAgendamento(ana, agora.minusMinutes(30), bia);
        confirmar(emAndamento);
        Agendamento comecouSemConfirmar = novoAgendamento(ana, agora.minusMinutes(10), bia);
        Agendamento futuro = novoAgendamento(ana, BASE, bia);

        rotina.processar();

        assertThat(statusDe(terminou)).isEqualTo(StatusAgendamento.CONCLUIDO);
        assertThat(statusDe(emAndamento)).isEqualTo(StatusAgendamento.CONFIRMADO);
        assertThat(statusDe(comecouSemConfirmar)).isEqualTo(StatusAgendamento.EXPIRADO);
        assertThat(statusDe(futuro)).isEqualTo(StatusAgendamento.PENDENTE);
        assertThat(historicoRepository.findByAgendamento_IdOrderByOcorridoEmAscIdAsc(terminou.getId()))
                .singleElement().satisfies(l -> {
                    assertThat(l.getAcao()).isEqualTo(AcaoAgendamento.CONCLUIDO);
                    assertThat(l.getAutorId()).isNull();
                });
        assertThat(notificacaoRepository.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getDestinatarioId()).isEqualTo(ana.getId());
            assertThat(n.getEvento().name()).isEqualTo("AGENDAMENTO_EXPIRADO");
        });
    }

    @Test
    void rodarDuasVezesNaoDuplicaHistoricoNemNotificacao() {
        LocalDateTime agora = LocalDateTime.now();
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        confirmar(novoAgendamento(ana, agora.minusHours(3), bia));
        novoAgendamento(ana, agora.minusMinutes(10), bia);

        rotina.processar();
        rotina.processar();

        assertThat(historicoRepository.count()).isEqualTo(2);
        assertThat(notificacaoRepository.count()).isEqualTo(1);
    }

    @Test
    void agendamentoSemDatasEhIgnorado() {
        UserModel ana = novoCliente("Ana");
        Agendamento semDatas = novoAgendamento(ana, BASE, novoChefe("Bia"));
        semDatas.setDataHoraInicial(null);
        semDatas.setDataHoraFinal(null);
        agendamentoRepository.save(semDatas);

        rotina.processar();

        assertThat(statusDe(semDatas)).isEqualTo(StatusAgendamento.PENDENTE);
    }

    @Test
    void falhaEmUmNaoImpedeOsOutros() {
        LocalDateTime agora = LocalDateTime.now();
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        Agendamento quebra = novoAgendamento(ana, agora.minusMinutes(20), bia);
        Agendamento segue = novoAgendamento(ana, agora.minusMinutes(10), bia);
        org.mockito.Mockito.doThrow(new IllegalStateException("falha simulada"))
                .when(agendamentoService).expirarPeloSistema(quebra.getId());

        rotina.processar();

        assertThat(statusDe(quebra)).isEqualTo(StatusAgendamento.PENDENTE);
        assertThat(statusDe(segue)).isEqualTo(StatusAgendamento.EXPIRADO);
    }

    @Test
    void chefConcluindoEnquantoARotinaConcluiFicaUmaConclusaoSo() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        Agendamento terminou = novoAgendamento(novoCliente("Ana"), LocalDateTime.now().minusHours(3), bia);
        confirmar(terminou);
        Cookie chef = cookieDe(bia);

        List<Integer> resultados = emParalelo(List.of(
                () -> mockMvc.perform(patch("/agendamentos/" + terminou.getId() + "/concluir").cookie(chef))
                        .andReturn().getResponse().getStatus(),
                () -> {
                    rotina.processar();
                    return 0;
                }));

        assertThat(resultados.get(0)).isIn(200, 409);
        assertThat(statusDe(terminou)).isEqualTo(StatusAgendamento.CONCLUIDO);
        assertThat(historicoRepository.findByAgendamento_IdOrderByOcorridoEmAscIdAsc(terminou.getId()))
                .filteredOn(l -> l.getAcao() == AcaoAgendamento.CONCLUIDO).hasSize(1);
    }

    private StatusAgendamento statusDe(Agendamento agendamento) {
        return agendamentoRepository.findById(agendamento.getId()).orElseThrow().getStatus();
    }

    private void confirmar(Agendamento agendamento) {
        agendamento.setStatus(com.couto.chefe_api.enums.StatusAgendamento.CONFIRMADO);
        agendamentoRepository.save(agendamento);
    }

    private ResultActions pedir(UserModel cliente, long enderecoId, LocalDateTime inicio, LocalDateTime fim,
                                ChefeModel... chefes) throws Exception {
        String ids = String.join(",", java.util.Arrays.stream(chefes).map(c -> "\"" + c.getId() + "\"").toList());
        return mockMvc.perform(post("/agendamentos").cookie(cookieDe(cliente)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataHoraInicial\":\"" + inicio.format(FORMATO) + "\",\"dataHoraFinal\":\"" + fim.format(FORMATO)
                        + "\",\"enderecoId\":" + enderecoId + ",\"chefeIds\":[" + ids + "],\"quantidadePessoas\":2}"));
    }

    private static long idDe(ResultActions resposta) throws Exception {
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(resposta.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static <T> List<T> emParalelo(List<Callable<T>> pedidos) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(pedidos.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (Callable<T> pedido : pedidos) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return pedido.call();
                }));
            }
            largada.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------------

    Agendamento novoAgendamento(UserModel cliente, LocalDateTime inicio, ChefeModel chefe) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(novoEndereco(cliente));
        agendamento.setChefe(chefe);
        agendamento.setDataHoraInicial(inicio);
        agendamento.setDataHoraFinal(inicio.plusHours(2));
        agendamento.setQuantidadePessoas(2);
        agendamento.setValorTotal(new BigDecimal("200.00"));
        return agendamentoRepository.save(agendamento);
    }

    Pratos novoPrato(ChefeModel chefe, String nome) {
        Pratos prato = new Pratos();
        prato.setNomePrato(nome);
        prato.setDescricao("Prato " + nome);
        prato.setImageUrl("https://img.test/" + nome + ".jpg");
        prato.setChefeModel(chefe);
        return pratosRepository.save(prato);
    }

    EnderecoModel novoEndereco(UserModel dono) {
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

    UserModel novoCliente(String nome) {
        UserModel usuario = new UserModel();
        usuario.setNome(nome);
        usuario.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        usuario.setEndereco(new ArrayList<>());
        return usuarioRepository.save(usuario);
    }

    ChefeModel novoChefe(String nome) {
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
        return chefeRepository.save(chefe);
    }

    Cookie cookieDe(UserModel usuario) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(usuario.getId(), usuario.getEmail(), TipoUsuario.CLIENTE)));
    }

    Cookie cookieDe(ChefeModel chefe) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(chefe.getId(), chefe.getEmail(), TipoUsuario.CHEFE)));
    }
}
