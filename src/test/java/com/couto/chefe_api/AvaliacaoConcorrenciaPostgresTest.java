package com.couto.chefe_api;

import com.couto.chefe_api.security.AuthCookies;
import com.couto.chefe_api.security.TokenService;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.Avaliacao;
import com.couto.chefe_api.repository.AvaliacaoRepository;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Dois clientes avaliando o mesmo chef ao mesmo tempo no Postgres real: nenhuma contagem se perde. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AvaliacaoConcorrenciaPostgresTest {

    static final LocalDateTime BASE = LocalDateTime.now().plusDays(10).withHour(18).withMinute(0).withSecond(0).withNano(0);

    @DynamicPropertySource
    static void bancoReal(DynamicPropertyRegistry registry) throws Exception {
        MigracoesPostgresTest.configurarBancoReal(registry);
    }

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired com.couto.chefe_api.repository.NotificacaoRepository notificacaoRepository;
    @Autowired AvaliacaoRepository avaliacaoRepository;

    final List<Agendamento> agendamentos = new ArrayList<>();
    ChefeModel bia;

    /** O banco é o mesmo do MigracoesPostgresTest: apaga só o que este teste criou. */
    @AfterEach
    void limparBanco() {
        agendamentoRepository.deleteAll(agendamentos);
        agendamentos.forEach(a -> {
            enderecoRepository.deleteById(a.getEndereco().getId());
            usuarioRepository.deleteById(a.getUsuario().getId());
        });
        notificacaoRepository.deleteAll(notificacaoRepository.findAll().stream()
                .filter(n -> n.getDestinatarioId().equals(bia.getId())).toList());
        chefeRepository.deleteById(bia.getId());
    }

    @Test
    void duasAvaliacoesSimultaneasDoMesmoChefSomamAsDuas() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bia = novoChefe();
        List<UserModel> clientes = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Agendamento agendamento = novoAgendamento(BASE.minusDays(20 + i));
            clientes.add(agendamento.getUsuario());
            agendamento.setStatus(StatusAgendamento.CONCLUIDO);
            agendamento.setConcluidoEm(LocalDateTime.now().minusDays(1));
            agendamentos.add(agendamentoRepository.save(agendamento));
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Integer> status = new ArrayList<>();
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < agendamentos.size(); i++) {
                Agendamento agendamento = agendamentos.get(i);
                UserModel cliente = clientes.get(i);
                Cookie cookie = new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                        new UsuarioAutenticado(cliente.getId(), cliente.getEmail(), TipoUsuario.CLIENTE)));
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return mockMvc.perform(post("/agendamentos/" + agendamento.getId() + "/avaliacoes").cookie(cookie)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"chefeId\":\"" + bia.getId() + "\",\"nota\":4}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            largada.countDown();
            for (Future<Integer> futuro : futuros) {
                status.add(futuro.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(status).containsOnly(201);
        ChefeModel depois = chefeRepository.findById(bia.getId()).orElseThrow();
        assertThat(depois.getTotalAvaliacoes()).isEqualTo(2);
        assertThat(depois.getSomaNotas()).isEqualTo(8);
    }

    @Test
    void avaliacaoRepetidaAoMesmoTempoResponde409ComAMensagemDoConflito() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bia = novoChefe();
        Agendamento agendamento = novoAgendamento(BASE.minusDays(20));
        agendamento.setStatus(StatusAgendamento.CONCLUIDO);
        agendamento.setConcluidoEm(LocalDateTime.now().minusDays(1));
        agendamentos.add(agendamentoRepository.save(agendamento));
        UserModel cliente = agendamento.getUsuario();
        Cookie cookie = new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(cliente.getId(), cliente.getEmail(), TipoUsuario.CLIENTE)));
        Callable<org.springframework.mock.web.MockHttpServletResponse> avaliar = () ->
                mockMvc.perform(post("/agendamentos/" + agendamento.getId() + "/avaliacoes").cookie(cookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"chefeId\":\"" + bia.getId() + "\",\"nota\":4}"))
                        .andReturn().getResponse();

        var respostas = juntos(avaliar, avaliar);

        assertThat(respostas).extracting(r -> r.getStatus()).containsExactlyInAnyOrder(201, 409);
        var repetida = respostas.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
        assertThat(repetida.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("Você já avaliou essa pessoa neste agendamento");
        ChefeModel depois = chefeRepository.findById(bia.getId()).orElseThrow();
        assertThat(depois.getTotalAvaliacoes()).isEqualTo(1);
        assertThat(depois.getSomaNotas()).isEqualTo(4);
    }

    @Test
    void denunciaRepetidaDeAvaliacaoAoMesmoTempoDaUm201EUm409() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bia = novoChefe();
        Agendamento agendamento = novoAgendamento(BASE.minusDays(20));
        agendamento.setStatus(StatusAgendamento.CONCLUIDO);
        agendamento.setConcluidoEm(LocalDateTime.now().minusDays(1));
        agendamentos.add(agendamentoRepository.save(agendamento));
        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setAgendamento(agendamento);
        avaliacao.setAutorId(agendamento.getUsuario().getId());
        avaliacao.setAutorTipo(TipoUsuario.CLIENTE);
        avaliacao.setAvaliadoId(bia.getId());
        avaliacao.setAvaliadoTipo(TipoUsuario.CHEFE);
        avaliacao.setNota(1);
        avaliacao.setComentario("ruim");
        avaliacao.setPublicadoEm(LocalDateTime.now());
        avaliacao = avaliacaoRepository.save(avaliacao);
        Cookie cookie = new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(bia.getId(), bia.getEmail(), TipoUsuario.CHEFE)));
        String url = "/avaliacoes/" + avaliacao.getId() + "/denuncias";
        Callable<org.springframework.mock.web.MockHttpServletResponse> denunciar = () ->
                mockMvc.perform(post(url).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"motivo\":\"OFENSIVA\"}"))
                        .andReturn().getResponse();

        var respostas = juntos(denunciar, denunciar);

        assertThat(respostas).extracting(r -> r.getStatus()).containsExactlyInAnyOrder(201, 409);
    }

    /** Dispara as chamadas na mesma largada e devolve as respostas. */
    @SafeVarargs
    private static List<org.springframework.mock.web.MockHttpServletResponse> juntos(
            Callable<org.springframework.mock.web.MockHttpServletResponse>... chamadas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(chamadas.length);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<org.springframework.mock.web.MockHttpServletResponse>> futuros = new ArrayList<>();
            for (var chamada : chamadas) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return chamada.call();
                }));
            }
            largada.countDown();
            List<org.springframework.mock.web.MockHttpServletResponse> respostas = new ArrayList<>();
            for (var futuro : futuros) {
                respostas.add(futuro.get(60, TimeUnit.SECONDS));
            }
            return respostas;
        } finally {
            pool.shutdownNow();
        }
    }

    private Agendamento novoAgendamento(LocalDateTime inicio) {
        UserModel cliente = new UserModel();
        cliente.setNome("Cliente");
        cliente.setEmail("cliente-" + UUID.randomUUID() + "@x.com");
        cliente.setEndereco(new ArrayList<>());
        cliente = usuarioRepository.save(cliente);
        EnderecoModel endereco = new EnderecoModel();
        endereco.setRua("Rua A");
        endereco.setNumero("1");
        endereco.setBairro("Centro");
        endereco.setCidade("São Paulo");
        endereco.setEstado("SP");
        endereco.setCep("01001-000");
        endereco.setTipoDeResidencia(TipoDeResidencia.CASA);
        endereco.setUsuario(cliente);
        endereco = enderecoRepository.save(endereco);
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(endereco);
        agendamento.setChefes(new ArrayList<>(List.of(bia)));
        agendamento.setDataHoraInicial(inicio);
        agendamento.setDataHoraFinal(inicio.plusHours(2));
        agendamento.setQuantidadePessoas(2);
        agendamento.setValorTotal(new BigDecimal("200.00"));
        return agendamentoRepository.save(agendamento);
    }

    private ChefeModel novoChefe() {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome("Bia");
        chefe.setEmail("bia-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("100"));
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        return chefeRepository.save(chefe);
    }
}
