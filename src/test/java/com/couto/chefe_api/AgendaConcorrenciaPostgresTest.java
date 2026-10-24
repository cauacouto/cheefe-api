package com.couto.chefe_api;

import com.couto.chefe_api.domain.AgendaBloqueio;
import com.couto.chefe_api.domain.AgendaDiaFixo;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendaBloqueioRepository;
import com.couto.chefe_api.repository.AgendaDiaFixoRepository;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.NotificacaoRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.security.AuthCookies;
import com.couto.chefe_api.security.TokenService;
import com.couto.chefe_api.security.UsuarioAutenticado;
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
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Agenda do chef no Postgres real: confirmar e bloquear o mesmo horário ao mesmo tempo nunca deixam um
 * confirmado dentro de um bloqueio, e apagar o chef leva a agenda junto (ON DELETE CASCADE).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AgendaConcorrenciaPostgresTest {

    static final LocalDateTime BASE = LocalDateTime.now().plusDays(10).withHour(18).withMinute(0).withSecond(0).withNano(0);
    static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

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
    @Autowired AgendaBloqueioRepository bloqueioRepository;
    @Autowired AgendaDiaFixoRepository diaFixoRepository;
    @Autowired NotificacaoRepository notificacaoRepository;

    final List<Agendamento> agendamentos = new ArrayList<>();
    final List<ChefeModel> chefes = new ArrayList<>();

    /** O banco é o mesmo do MigracoesPostgresTest: apaga só o que este teste criou. */
    @AfterEach
    void limparBanco() {
        agendamentoRepository.deleteAll(agendamentos);
        List<UUID> donos = new ArrayList<>(chefes.stream().map(ChefeModel::getId).toList());
        agendamentos.forEach(a -> {
            donos.add(a.getUsuario().getId());
            enderecoRepository.deleteById(a.getEndereco().getId());
            usuarioRepository.deleteById(a.getUsuario().getId());
        });
        notificacaoRepository.deleteAll(notificacaoRepository.findAll().stream()
                .filter(n -> donos.contains(n.getDestinatarioId())).toList());
        chefes.forEach(c -> chefeRepository.deleteById(c.getId())); // a agenda sai junto
    }

    @Test
    void confirmarEBloquearAoMesmoTempoNuncaDeixamConfirmadoDentroDeBloqueio() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        int confirmadas = 0;
        int bloqueadas = 0;
        for (int rodada = 0; rodada < 8; rodada++) {
            ChefeModel bia = novoChefe();
            Agendamento pedido = novoAgendamento(bia, BASE.plusDays(rodada));
            Cookie cookie = cookieDe(bia);
            String corpo = "{\"inicio\":\"" + BASE.plusDays(rodada).minusHours(1).format(FORMATO)
                    + "\",\"fim\":\"" + BASE.plusDays(rodada).plusHours(1).format(FORMATO) + "\"}";

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch largada = new CountDownLatch(1);
            int statusConfirmar;
            int statusBloquear;
            try {
                Future<Integer> confirmar = pool.submit(() -> {
                    largada.await();
                    return mockMvc.perform(patch("/agendamentos/" + pedido.getId() + "/confirmar").cookie(cookie))
                            .andReturn().getResponse().getStatus();
                });
                Future<Integer> bloquear = pool.submit(() -> {
                    largada.await();
                    return mockMvc.perform(post("/chefe/me/agenda/bloqueios").cookie(cookie)
                                    .contentType(MediaType.APPLICATION_JSON).content(corpo))
                            .andReturn().getResponse().getStatus();
                });
                largada.countDown();
                statusConfirmar = confirmar.get(60, TimeUnit.SECONDS);
                statusBloquear = bloquear.get(60, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }

            // exatamente um vence: quem chega primeiro; o outro recebe 409
            boolean confirmou = statusConfirmar == 200;
            boolean bloqueou = statusBloquear == 201;
            assertThat(confirmou ^ bloqueou).as("rodada %d: confirmar=%d bloquear=%d", rodada, statusConfirmar, statusBloquear)
                    .isTrue();
            assertThat(confirmou ? statusBloquear : statusConfirmar).isEqualTo(409);
            StatusAgendamento depois = agendamentoRepository.findById(pedido.getId()).orElseThrow().getStatus();
            assertThat(depois).isEqualTo(confirmou ? StatusAgendamento.CONFIRMADO : StatusAgendamento.PENDENTE);
            assertThat(bloqueioRepository.countByChefe_IdAndFimAfter(bia.getId(), LocalDateTime.now()))
                    .isEqualTo(bloqueou ? 1 : 0);
            confirmadas += confirmou ? 1 : 0;
            bloqueadas += bloqueou ? 1 : 0;
        }
        assertThat(confirmadas + bloqueadas).isEqualTo(8);
    }

    @Test
    void apagarOChefApagaABloqueiosEDiasFixos() {
        ChefeModel bia = novoChefe();
        AgendaBloqueio bloqueio = new AgendaBloqueio();
        bloqueio.setChefe(bia);
        bloqueio.setInicio(BASE);
        bloqueio.setFim(BASE.plusDays(1));
        bloqueio.setCriadoEm(LocalDateTime.now());
        bloqueio = bloqueioRepository.save(bloqueio);
        diaFixoRepository.save(new AgendaDiaFixo(bia, DayOfWeek.MONDAY));
        assertThat(diaFixoRepository.doChefe(bia.getId())).hasSize(1);

        chefeRepository.deleteById(bia.getId());
        chefeRepository.flush();

        assertThat(bloqueioRepository.findById(bloqueio.getId())).isEmpty();
        assertThat(diaFixoRepository.doChefe(bia.getId())).isEmpty();
        chefes.remove(bia);
    }

    private Agendamento novoAgendamento(ChefeModel chefe, LocalDateTime inicio) {
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
        agendamento.setChefes(new ArrayList<>(List.of(chefe)));
        agendamento.setDataHoraInicial(inicio);
        agendamento.setDataHoraFinal(inicio.plusHours(2));
        agendamento.setQuantidadePessoas(2);
        agendamento.setValorTotal(new BigDecimal("200.00"));
        agendamento = agendamentoRepository.save(agendamento);
        agendamentos.add(agendamento);
        return agendamento;
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
        chefe = chefeRepository.save(chefe);
        chefes.add(chefe);
        return chefe;
    }

    private Cookie cookieDe(ChefeModel chefe) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(chefe.getId(), chefe.getEmail(), TipoUsuario.CHEFE)));
    }
}
