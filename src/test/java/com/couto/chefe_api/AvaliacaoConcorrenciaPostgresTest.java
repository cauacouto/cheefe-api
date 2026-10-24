package com.couto.chefe_api;

import com.couto.chefe_api.security.AuthCookies;
import com.couto.chefe_api.security.TokenService;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.Agendamento;
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

    private Agendamento novoAgendamento(LocalDateTime inicio) {
        UserModel cliente = new UserModel();
        cliente.setNome("Cliente");
        cliente.setEmail("cliente-" + UUID.randomUUID() + "@x.com");
        cliente.setEndereco(new ArrayList<>());
        cliente = usuarioRepository.save(TelefoneDeTeste.verificado(cliente));
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
        return chefeRepository.save(TelefoneDeTeste.verificado(chefe));
    }
}
