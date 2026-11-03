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

/** Confirmar um pedido e mudar a folga do chef ao mesmo tempo no Postgres real: a trava no chef serializa os dois. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FolgaConcorrenciaPostgresTest {

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
    void confirmarEMudarAFolgaAoMesmoTempoSerializam() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bia = novoChefe();
        menus.ativo(bia);
        Cookie chef = new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(bia.getId(), bia.getEmail(), TipoUsuario.CHEFE)));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int rodada = 0; rodada < 8; rodada++) {
                // folga zerada; um confirmado das 18h às 20h e um pendente que começa 30 min depois dele
                ChefeModel chefe = chefeRepository.findById(bia.getId()).orElseThrow();
                chefe.setFolgaMinutos(0);
                chefeRepository.save(chefe);
                LocalDateTime dia = BASE.plusDays(rodada);
                Agendamento confirmado = novoAgendamento(dia);
                confirmado.setStatus(StatusAgendamento.CONFIRMADO);
                agendamentos.add(agendamentoRepository.save(confirmado));
                Agendamento pendente = novoAgendamento(dia.plusMinutes(150));
                agendamentos.add(pendente);

                CountDownLatch largada = new CountDownLatch(1);
                Future<Integer> confirmacao = pool.submit(() -> {
                    largada.await();
                    return mockMvc.perform(patch("/agendamentos/" + pendente.getId() + "/confirmar").cookie(chef))
                            .andReturn().getResponse().getStatus();
                });
                Future<Integer> troca = pool.submit(() -> {
                    largada.await();
                    return mockMvc.perform(patch("/chefe/me").cookie(chef).contentType("application/json")
                                    .content("{\"folgaMinutos\":60}"))
                            .andReturn().getResponse().getStatus();
                });
                largada.countDown();
                int statusConfirmacao = confirmacao.get(60, TimeUnit.SECONDS);
                int statusTroca = troca.get(60, TimeUnit.SECONDS);

                // dois desfechos possíveis: confirmou antes da troca (os dois confirmados ficam, a troca não derruba
                // ninguém) ou a troca veio primeiro e a folga de 60 recusou a confirmação
                assertThat(statusTroca).isEqualTo(200);
                assertThat(chefeRepository.findById(bia.getId()).orElseThrow().getFolgaMinutos()).isEqualTo(60);
                StatusAgendamento final_ = agendamentoRepository.findById(pendente.getId()).orElseThrow().getStatus();
                assertThat(statusConfirmacao).isIn(200, 409);
                assertThat(final_).isEqualTo(statusConfirmacao == 200 ? StatusAgendamento.CONFIRMADO : StatusAgendamento.PENDENTE);
                assertThat(agendamentoRepository.findById(confirmado.getId()).orElseThrow().getStatus())
                        .isEqualTo(StatusAgendamento.CONFIRMADO);
            }
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
