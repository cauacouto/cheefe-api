package com.couto.chefe_api;

import com.couto.chefe_api.security.AuthCookies;
import com.couto.chefe_api.security.TokenService;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.ConversaRepository;
import com.couto.chefe_api.repository.MensagemRepository;
import com.couto.chefe_api.repository.NotificacaoRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
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

/**
 * As garantias de concorrência do chat no Postgres real e pela API (com o EntityManager da requisição):
 * no Postgres o segundo INSERT espera o primeiro commit e o FOR UPDATE bloqueia de verdade, diferente do H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(FiltroDeTeste.class)
@Testcontainers(disabledWithoutDocker = true)
class ChatConcorrenciaPostgresTest {

    static final int PEDIDOS = 8;
    static final JsonMapper JSON = JsonMapper.builder().build();

    @DynamicPropertySource
    static void bancoReal(DynamicPropertyRegistry registry) throws Exception {
        MigracoesPostgresTest.configurarBancoReal(registry);
    }

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired ConversaRepository conversaRepository;
    @Autowired MensagemRepository mensagemRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired TransactionTemplate transacao;
    @Autowired com.couto.chefe_api.repository.BloqueioContaRepository bloqueioContaRepository;

    MockMvc mockMvc;
    UserModel ana;
    ChefeModel bia;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ana = new UserModel();
        ana.setNome("Ana");
        ana.setEmail("ana-" + UUID.randomUUID() + "@x.com");
        ana.setEndereco(new ArrayList<>());
        ana = usuarioRepository.save(ana);
        bia = new ChefeModel();
        bia.setNome("Bia");
        bia.setEmail("bia-" + UUID.randomUUID() + "@x.com");
        bia.setNacionalidade("Brasileira");
        bia.setIdiomas("pt");
        bia.setValorHora(new BigDecimal("100"));
        bia.setEspecialidade(Especialidades.BRASILEIRA);
        bia.setSenioridade(Senioridade.HEAD_CHEF);
        bia.setAnosDeExperiencia(5);
        bia = chefeRepository.save(bia);
    }

    /** O banco é o mesmo do MigracoesPostgresTest: apaga só o que este teste criou. */
    @AfterEach
    void limparBanco() {
        transacao.executeWithoutResult(status -> {
            notificacaoRepository.apagarDoDestinatario(bia.getId());
            notificacaoRepository.deleteAll(notificacaoRepository.findAll().stream()
                    .filter(n -> n.getEvento() == com.couto.chefe_api.enums.EventoNotificacao.CONTA_BLOQUEADA).toList());
            bloqueioContaRepository.deleteByContaId(ana.getId());
            mensagemRepository.deleteByConversa_Cliente_Id(ana.getId());
            conversaRepository.deleteByCliente_Id(ana.getId());
            usuarioRepository.deleteById(ana.getId());
            chefeRepository.deleteById(bia.getId());
        });
    }

    @Test
    void abrirAMesmaConversaAoMesmoTempoPelaApi() throws Exception {
        List<MockHttpServletResponse> respostas = emParalelo(() -> mockMvc.perform(post("/conversas").cookie(cookieAna())
                .contentType(MediaType.APPLICATION_JSON).content("{\"chefeId\":\"" + bia.getId() + "\"}")).andReturn().getResponse());

        assertThat(respostas).extracting(MockHttpServletResponse::getStatus).containsOnly(200, 201);
        assertThat(respostas).filteredOn(r -> r.getStatus() == 201).hasSize(1);
        assertThat(respostas).extracting(this::id).containsOnly(id(respostas.get(0)));
        assertThat(conversaRepository.count()).isEqualTo(1);
    }

    @Test
    void mensagensSimultaneasPelaApiGeramUmaNotificacao() throws Exception {
        long conversa = id(mockMvc.perform(post("/conversas").cookie(cookieAna()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"chefeId\":\"" + bia.getId() + "\"}")).andReturn().getResponse());

        List<MockHttpServletResponse> respostas = emParalelo(() -> mockMvc.perform(post("/conversas/" + conversa + "/mensagens")
                .cookie(cookieAna()).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}")).andReturn().getResponse());

        assertThat(respostas).extracting(MockHttpServletResponse::getStatus).containsOnly(201);
        assertThat(mensagemRepository.count()).isEqualTo(PEDIDOS);
        assertThat(notificacaoRepository.countByDestinatarioIdAndLidaFalse(bia.getId())).isEqualTo(1);
    }

    /**
     * O bloqueio do autor roda numa transação própria (REQUIRES_NEW): o filtro tem que rodar antes da
     * trava da conversa. Com a trava de outra transação em aberto, o ódio ainda recebe 403 na hora.
     */
    @Test
    void odioNoChatBloqueiaSemEsperarATravaDaConversa() throws Exception {
        long conversa = id(mockMvc.perform(post("/conversas").cookie(cookieAna()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"chefeId\":\"" + bia.getId() + "\"}")).andReturn().getResponse());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch travada = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        try {
            Future<?> quemTrava = pool.submit(() -> transacao.executeWithoutResult(status -> {
                conversaRepository.travarPorId(conversa); // SELECT ... FOR UPDATE, segurada até soltar
                travada.countDown();
                try {
                    soltar.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(travada.await(30, TimeUnit.SECONDS)).isTrue();

            Future<MockHttpServletResponse> envio = pool.submit(() -> mockMvc.perform(post("/conversas/" + conversa + "/mensagens")
                    .cookie(cookieAna()).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"seu odiento\"}"))
                    .andReturn().getResponse());

            // sem esperar a trava: se o filtro rodasse depois dela, isto estouraria o tempo
            MockHttpServletResponse resposta = envio.get(15, TimeUnit.SECONDS);
            assertThat(resposta.getStatus()).isEqualTo(403);
            assertThat(usuarioRepository.findById(ana.getId()).orElseThrow().getBloqueadoEm()).isNotNull();
            assertThat(mensagemRepository.count()).isZero();

            soltar.countDown();
            quemTrava.get(30, TimeUnit.SECONDS);
        } finally {
            soltar.countDown();
            pool.shutdownNow();
        }
    }

    private long id(MockHttpServletResponse resposta) {
        try {
            return JSON.readTree(resposta.getContentAsString()).get("id").asLong();
        } catch (Exception e) {
            throw new IllegalStateException("resposta sem id: status " + resposta.getStatus(), e);
        }
    }

    private Cookie cookieAna() {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(ana.getId(), ana.getEmail(), TipoUsuario.CLIENTE)));
    }

    private <T> List<T> emParalelo(Callable<T> pedido) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(PEDIDOS);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (int i = 0; i < PEDIDOS; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return pedido.call();
                }));
            }
            largada.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                resultados.add(futuro.get(60, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }
}
