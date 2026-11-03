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
import com.couto.chefe_api.repository.MenuRepository;
import com.couto.chefe_api.repository.PratosRepository;
import com.couto.chefe_api.domain.Menu;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Confirmações simultâneas do mesmo horário no Postgres real: a trava no chef deixa passar só uma. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AgendamentoConcorrenciaPostgresTest {

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
    @Autowired MenuRepository menuRepository;
    @Autowired PratosRepository pratosRepository;
    @Autowired MenuDeTeste menus;

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
        // o menu e o prato do teste (os itens saem em cascata)
        menuRepository.deleteAll(menuRepository.findAll().stream().filter(m -> m.getChefe().getId().equals(bia.getId())).toList());
        pratosRepository.deleteAll(pratosRepository.findAll().stream()
                .filter(p -> p.getChefeModel().getId().equals(bia.getId())).toList());
        chefeRepository.deleteById(bia.getId());
    }

    @Test
    void confirmacoesSimultaneasDoMesmoHorarioSoUmaPassa() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bia = novoChefe();
        for (int i = 0; i < 6; i++) {
            agendamentos.add(novoAgendamento(BASE.plusMinutes(10L * i)));
        }
        Cookie chef = new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(bia.getId(), bia.getEmail(), TipoUsuario.CHEFE)));

        ExecutorService pool = Executors.newFixedThreadPool(agendamentos.size());
        CountDownLatch largada = new CountDownLatch(1);
        List<Integer> status = new ArrayList<>();
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (Agendamento agendamento : agendamentos) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return mockMvc.perform(patch("/agendamentos/" + agendamento.getId() + "/confirmar").cookie(chef))
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

        assertThat(status).filteredOn(s -> s == 200).hasSize(1);
        assertThat(status).filteredOn(s -> s == 409).hasSize(5);
        assertThat(agendamentoRepository.findAllById(agendamentos.stream().map(Agendamento::getId).toList()))
                .filteredOn(a -> a.getStatus() == StatusAgendamento.CONFIRMADO).hasSize(1);
    }

    /** O PUT do cliente e o confirm do chef disputam a mesma linha: um espera o outro, o estado final é coerente. */
    @Test
    void putContraConfirmSerializaSemPerderEstado() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bia = novoChefe();
        Menu menu = menus.ativo(bia);
        Cookie chef = new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(bia.getId(), bia.getEmail(), TipoUsuario.CHEFE)));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int rodada = 0; rodada < 6; rodada++) {
                Agendamento criado = novoAgendamento(BASE.plusDays(rodada));
                criado.aplicarMenu(menu, 2);
                Agendamento agendamento = agendamentoRepository.save(criado);
                agendamentos.add(agendamento);
                Cookie cliente = new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(new UsuarioAutenticado(
                        criado.getUsuario().getId(), criado.getUsuario().getEmail(), TipoUsuario.CLIENTE)));

                CountDownLatch largada = new CountDownLatch(1);
                Future<Integer> edicao = pool.submit(() -> {
                    largada.await();
                    return mockMvc.perform(put("/agendamentos/" + agendamento.getId()).cookie(cliente)
                                    .contentType("application/json").content("{\"quantidadePessoas\":3}"))
                            .andReturn().getResponse().getStatus();
                });
                Future<Integer> confirmacao = pool.submit(() -> {
                    largada.await();
                    return mockMvc.perform(patch("/agendamentos/" + agendamento.getId() + "/confirmar").cookie(chef))
                            .andReturn().getResponse().getStatus();
                });
                largada.countDown();
                int statusEdicao = edicao.get(60, TimeUnit.SECONDS);
                int statusConfirmacao = confirmacao.get(60, TimeUnit.SECONDS);

                Agendamento final_ = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
                // dois desfechos possíveis, ambos com o confirm valendo e nenhuma escrita perdida:
                // editou e depois confirmou (3 pessoas), ou confirmou e a edição foi recusada (2 pessoas)
                assertThat(statusConfirmacao).isEqualTo(200);
                assertThat(final_.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
                if (statusEdicao == 200) {
                    assertThat(final_.getQuantidadePessoas()).isEqualTo(3);
                    assertThat(final_.getValorTotal()).isEqualByComparingTo("300.00");
                } else {
                    assertThat(statusEdicao).isEqualTo(409);
                    assertThat(final_.getQuantidadePessoas()).isEqualTo(2);
                    assertThat(final_.getValorTotal()).isEqualByComparingTo("200.00");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void chefLeOHistoricoNoPostgres() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bia = novoChefe();
        Agendamento agendamento = novoAgendamento(BASE);
        agendamentos.add(agendamento);
        Cookie chef = new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(bia.getId(), bia.getEmail(), TipoUsuario.CHEFE)));

        // a leitura roda em transação somente leitura: no Postgres um FOR UPDATE ali é erro
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/agendamentos/" + agendamento.getId() + "/historico").cookie(chef))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
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
        agendamento.setChefe(bia);
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
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setEspecialidades(Set.of(Especialidades.BRASILEIRA));
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        return chefeRepository.save(chefe);
    }
}
