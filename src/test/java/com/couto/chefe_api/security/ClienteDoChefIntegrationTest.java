package com.couto.chefe_api.security;

import com.couto.chefe_api.FiltroDeTeste;
import com.couto.chefe_api.MenuDeTeste;
import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.Menu;
import com.couto.chefe_api.domain.NotaCliente;
import com.couto.chefe_api.domain.NotaClienteId;
import com.couto.chefe_api.domain.RestricaoAgendamento;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.OcasiaoMenu;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoRestricao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.NotaClienteRepository;
import com.couto.chefe_api.repository.PratosRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Meus clientes: lista, histórico com o chef e nota privada. Tudo vale só para os agendamentos do próprio chef. */
@SpringBootTest
@Import(FiltroDeTeste.class)
@ActiveProfiles("test")
class ClienteDoChefIntegrationTest {

    static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    /** Minutos cheios: o formato da API não tem segundos. */
    static final LocalDateTime AGORA = LocalDateTime.now().withSecond(0).withNano(0);

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired NotaClienteRepository notaRepository;
    @Autowired PratosRepository pratosRepository;
    @Autowired MenuDeTeste menus;
    @Autowired EntityManagerFactory entityManagerFactory;

    MockMvc mockMvc;
    ChefeModel bia;
    ChefeModel caio;
    UserModel ana;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bia = novoChefe("Bia");
        caio = novoChefe("Caio");
        ana = novoCliente("Ana Souza");
    }

    @AfterEach
    void limparBanco() {
        notaRepository.deleteAll();
        agendamentoRepository.deleteAll();
        pratosRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    // ─── lista ───────────────────────────────────────────────────────────

    @Test
    void listaSoClientesComConfirmadoOuConcluido() throws Exception {
        novoAgendamento(ana, bia, AGORA.plusDays(3), StatusAgendamento.CONFIRMADO);
        UserModel mia = novoCliente("Mia");
        novoAgendamento(mia, bia, AGORA.minusDays(3), StatusAgendamento.CONCLUIDO);
        novoAgendamento(novoCliente("Edu"), bia, AGORA.plusDays(1), StatusAgendamento.PENDENTE);
        novoAgendamento(novoCliente("Zé"), bia, AGORA.plusDays(1), StatusAgendamento.RECUSADO);
        novoAgendamento(novoCliente("Lia"), bia, AGORA.plusDays(1), StatusAgendamento.CANCELADO);
        novoAgendamento(novoCliente("Rui"), bia, AGORA.minusDays(1), StatusAgendamento.EXPIRADO);
        novoAgendamento(novoCliente("Teo"), caio, AGORA.plusDays(1), StatusAgendamento.CONFIRMADO); // de outro chef

        mockMvc.perform(get("/clientes").cookie(cookieDe(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(ana.getId().toString()))
                .andExpect(jsonPath("$.content[0].primeiroNome").value("Ana"))
                .andExpect(jsonPath("$.content[0].foto").value("https://fotos/ana-souza.png"))
                .andExpect(jsonPath("$.content[1].id").value(mia.getId().toString()));
    }

    @Test
    void ordemPeloAgendamentoMaisRecente() throws Exception {
        UserModel antiga = novoCliente("Antiga");
        UserModel recente = novoCliente("Recente");
        UserModel meio = novoCliente("Meio");
        novoAgendamento(antiga, bia, AGORA.minusDays(30), StatusAgendamento.CONCLUIDO);
        novoAgendamento(recente, bia, AGORA.minusDays(1), StatusAgendamento.CONCLUIDO);
        novoAgendamento(meio, bia, AGORA.minusDays(10), StatusAgendamento.CONCLUIDO);
        // o agendamento futuro também é "o mais recente": quem já tem um marcado vem primeiro
        novoAgendamento(antiga, bia, AGORA.plusDays(20), StatusAgendamento.CONFIRMADO);

        mockMvc.perform(get("/clientes").param("sort", "primeiroNome,asc").cookie(cookieDe(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(antiga.getId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(recente.getId().toString()))
                .andExpect(jsonPath("$.content[2].id").value(meio.getId().toString()));
    }

    @Test
    void ultimoEProximoAgendamento() throws Exception {
        novoAgendamento(ana, bia, AGORA.minusDays(10), StatusAgendamento.CONCLUIDO);
        novoAgendamento(ana, bia, AGORA.minusDays(3), StatusAgendamento.CONCLUIDO);
        novoAgendamento(ana, bia, AGORA.plusDays(5), StatusAgendamento.CONFIRMADO);
        novoAgendamento(ana, bia, AGORA.plusDays(2), StatusAgendamento.CONFIRMADO);
        novoAgendamento(ana, bia, AGORA.plusDays(1), StatusAgendamento.PENDENTE);   // não conta
        novoAgendamento(ana, bia, AGORA.minusDays(1), StatusAgendamento.CANCELADO); // não conta
        novoAgendamento(ana, caio, AGORA.plusHours(1), StatusAgendamento.CONFIRMADO); // é de outro chef
        UserModel mia = novoCliente("Mia");
        novoAgendamento(mia, bia, AGORA.minusDays(40), StatusAgendamento.CONCLUIDO);

        mockMvc.perform(get("/clientes").cookie(cookieDe(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ana.getId().toString()))
                .andExpect(jsonPath("$.content[0].totalAgendamentos").value(4))
                .andExpect(jsonPath("$.content[0].ultimoAgendamento").value(AGORA.minusDays(3).format(FORMATO)))
                .andExpect(jsonPath("$.content[0].proximoAgendamento").value(AGORA.plusDays(2).format(FORMATO)))
                .andExpect(jsonPath("$.content[1].id").value(mia.getId().toString()))
                .andExpect(jsonPath("$.content[1].totalAgendamentos").value(1))
                .andExpect(jsonPath("$.content[1].proximoAgendamento").value(nullValue()));
    }

    @Test
    void listaTemPaginaDeNoMaximo50ESemNMais1() throws Exception {
        for (int i = 0; i < 3; i++) {
            novoAgendamento(novoCliente("Cliente" + i), bia, AGORA.minusDays(i + 1), StatusAgendamento.CONCLUIDO);
        }
        long comTres = queries(get("/clientes").cookie(cookieDe(bia)));
        for (int i = 3; i < 8; i++) {
            novoAgendamento(novoCliente("Cliente" + i), bia, AGORA.minusDays(i + 1), StatusAgendamento.CONCLUIDO);
        }

        assertThat(queries(get("/clientes").cookie(cookieDe(bia)))).isEqualTo(comTres);
        mockMvc.perform(get("/clientes").param("size", "500").cookie(cookieDe(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(50))
                .andExpect(jsonPath("$.page.totalElements").value(8));
    }

    // ─── histórico ───────────────────────────────────────────────────────

    @Test
    void historicoSoDesteChef() throws Exception {
        Agendamento comBia1 = novoAgendamento(ana, bia, AGORA.minusDays(5), StatusAgendamento.CONCLUIDO);
        Agendamento comBia2 = novoAgendamento(ana, bia, AGORA.plusDays(5), StatusAgendamento.CONFIRMADO);
        Agendamento comCaio = novoAgendamento(ana, caio, AGORA.minusDays(2), StatusAgendamento.CONCLUIDO);
        gravarNota(bia, ana, "Só da Bia");

        mockMvc.perform(get("/clientes/" + ana.getId() + "/historico").cookie(cookieDe(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.id").value(ana.getId().toString()))
                .andExpect(jsonPath("$.cliente.primeiroNome").value("Ana"))
                .andExpect(jsonPath("$.cliente.foto").value("https://fotos/ana-souza.png"))
                .andExpect(jsonPath("$.reputacao.totalAvaliacoes").value(0))
                .andExpect(jsonPath("$.nota.texto").value("Só da Bia"))
                .andExpect(jsonPath("$.agendamentos.length()").value(2))
                .andExpect(jsonPath("$.agendamentos[0].id").value(comBia2.getId()))
                .andExpect(jsonPath("$.agendamentos[0].dataHoraInicial").value(AGORA.plusDays(5).format(FORMATO)))
                .andExpect(jsonPath("$.agendamentos[0].status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.agendamentos[0].menu.nome").value("Menu de teste"))
                .andExpect(jsonPath("$.agendamentos[0].quantidadePessoas").value(2))
                .andExpect(jsonPath("$.agendamentos[0].ocasiao").value("FAMILIA"))
                .andExpect(jsonPath("$.agendamentos[1].id").value(comBia1.getId()));

        // o Caio vê só o dele, e a nota da Bia não existe para ele
        mockMvc.perform(get("/clientes/" + ana.getId() + "/historico").cookie(cookieDe(caio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agendamentos.length()").value(1))
                .andExpect(jsonPath("$.agendamentos[0].id").value(comCaio.getId()))
                .andExpect(jsonPath("$.nota").value(nullValue()));
    }

    @Test
    void historicoDeClienteNuncaAtendidoDa404() throws Exception {
        mockMvc.perform(get("/clientes/" + ana.getId() + "/historico").cookie(cookieDe(bia)))
                .andExpect(status().isNotFound());

        // recusar apaga o cliente da vista do chef
        novoAgendamento(ana, bia, AGORA.plusDays(1), StatusAgendamento.RECUSADO);
        mockMvc.perform(get("/clientes/" + ana.getId() + "/historico").cookie(cookieDe(bia)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/clientes/" + UUID.randomUUID() + "/historico").cookie(cookieDe(bia)))
                .andExpect(status().isNotFound());
    }

    @Test
    void restricoesFrequentesContamPorAgendamento() throws Exception {
        novoAgendamento(ana, bia, AGORA.minusDays(9), StatusAgendamento.CONCLUIDO,
                new RestricaoAgendamento(TipoRestricao.VEGANO, 5), new RestricaoAgendamento(TipoRestricao.SEM_GLUTEN, 1));
        novoAgendamento(ana, bia, AGORA.plusDays(4), StatusAgendamento.CONFIRMADO,
                new RestricaoAgendamento(TipoRestricao.VEGANO, 1));
        novoAgendamento(ana, bia, AGORA.plusDays(6), StatusAgendamento.CONFIRMADO,
                new RestricaoAgendamento(TipoRestricao.VEGANO, 1));
        novoAgendamento(ana, bia, AGORA.plusDays(3), StatusAgendamento.CONFIRMADO,
                new RestricaoAgendamento(TipoRestricao.SEM_GLUTEN, 2), new RestricaoAgendamento(TipoRestricao.ALERGIA_OVO, 1));
        // não contam: pendente, cancelado e o de outro chef
        novoAgendamento(ana, bia, AGORA.plusDays(8), StatusAgendamento.PENDENTE, new RestricaoAgendamento(TipoRestricao.ALERGIA_OVO, 1));
        novoAgendamento(ana, bia, AGORA.minusDays(2), StatusAgendamento.CANCELADO, new RestricaoAgendamento(TipoRestricao.ALERGIA_OVO, 1));
        novoAgendamento(ana, caio, AGORA.minusDays(2), StatusAgendamento.CONCLUIDO, new RestricaoAgendamento(TipoRestricao.ALERGIA_OVO, 1));

        // quantas pessoas não importa: cada tipo conta uma vez por agendamento, do maior para o menor
        mockMvc.perform(get("/clientes/" + ana.getId() + "/historico").cookie(cookieDe(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restricoesFrequentes.length()").value(3))
                .andExpect(jsonPath("$.restricoesFrequentes[0].tipo").value("VEGANO"))
                .andExpect(jsonPath("$.restricoesFrequentes[0].agendamentos").value(3))
                .andExpect(jsonPath("$.restricoesFrequentes[1].tipo").value("SEM_GLUTEN"))
                .andExpect(jsonPath("$.restricoesFrequentes[1].agendamentos").value(2))
                .andExpect(jsonPath("$.restricoesFrequentes[2].tipo").value("ALERGIA_OVO"))
                .andExpect(jsonPath("$.restricoesFrequentes[2].agendamentos").value(1));
    }

    @Test
    void vinteMaisRecentes() throws Exception {
        for (int i = 1; i <= 25; i++) {
            novoAgendamento(ana, bia, AGORA.minusDays(i), StatusAgendamento.CONCLUIDO);
        }

        mockMvc.perform(get("/clientes/" + ana.getId() + "/historico").cookie(cookieDe(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agendamentos.length()").value(20))
                .andExpect(jsonPath("$.agendamentos[0].dataHoraInicial").value(AGORA.minusDays(1).format(FORMATO)))
                .andExpect(jsonPath("$.agendamentos[19].dataHoraInicial").value(AGORA.minusDays(20).format(FORMATO)));
        // as 25 contam na lista, o corte é só do histórico
        mockMvc.perform(get("/clientes").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.content[0].totalAgendamentos").value(25));
    }

    @Test
    void historicoSemRecusadoECanceladoSemRestricoes() throws Exception {
        RestricaoAgendamento vegano = new RestricaoAgendamento(TipoRestricao.VEGANO, 2);
        Agendamento confirmado = novoAgendamento(ana, bia, AGORA.plusDays(4), StatusAgendamento.CONFIRMADO, vegano);
        novoAgendamento(ana, bia, AGORA.plusDays(3), StatusAgendamento.RECUSADO, vegano);
        Agendamento cancelado = novoAgendamento(ana, bia, AGORA.plusDays(2), StatusAgendamento.CANCELADO, vegano);
        Agendamento expirado = novoAgendamento(ana, bia, AGORA.minusDays(2), StatusAgendamento.EXPIRADO, vegano);

        mockMvc.perform(get("/clientes/" + ana.getId() + "/historico").cookie(cookieDe(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agendamentos.length()").value(3))
                .andExpect(jsonPath("$.agendamentos[0].id").value(confirmado.getId()))
                .andExpect(jsonPath("$.agendamentos[0].restricoes.length()").value(1))
                .andExpect(jsonPath("$.agendamentos[0].restricoes[0].tipo").value("VEGANO"))
                .andExpect(jsonPath("$.agendamentos[0].restricoes[0].quantidade").value(2))
                .andExpect(jsonPath("$.agendamentos[1].id").value(cancelado.getId()))
                .andExpect(jsonPath("$.agendamentos[1].status").value("CANCELADO"))
                .andExpect(jsonPath("$.agendamentos[1].restricoes.length()").value(0))
                .andExpect(jsonPath("$.agendamentos[2].id").value(expirado.getId()))
                .andExpect(jsonPath("$.agendamentos[2].restricoes.length()").value(0))
                // só o confirmado conta nas frequentes
                .andExpect(jsonPath("$.restricoesFrequentes.length()").value(1))
                .andExpect(jsonPath("$.restricoesFrequentes[0].agendamentos").value(1));
    }

    @Test
    void historicoSemNMais1() throws Exception {
        novoAgendamento(ana, bia, AGORA.minusDays(1), StatusAgendamento.CONCLUIDO, new RestricaoAgendamento(TipoRestricao.VEGANO, 1));
        long comUm = queries(get("/clientes/" + ana.getId() + "/historico").cookie(cookieDe(bia)));
        for (int i = 2; i < 12; i++) {
            novoAgendamento(ana, bia, AGORA.minusDays(i), StatusAgendamento.CONCLUIDO, new RestricaoAgendamento(TipoRestricao.VEGANO, 1));
        }

        assertThat(queries(get("/clientes/" + ana.getId() + "/historico").cookie(cookieDe(bia)))).isEqualTo(comUm);
    }

    // ─── nota ────────────────────────────────────────────────────────────

    @Test
    void notaCriadaSubstituidaEApagadaComTextoVazio() throws Exception {
        novoAgendamento(ana, bia, AGORA.plusDays(1), StatusAgendamento.CONFIRMADO);

        gravarPelaApi(bia, ana, "Prefere vinho tinto")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("Prefere vinho tinto"))
                .andExpect(jsonPath("$.atualizadaEm").isNotEmpty());
        gravarPelaApi(bia, ana, "Cozinha pequena, levar fogareiro")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("Cozinha pequena, levar fogareiro"));

        // uma nota por par chef e cliente: a segunda substituiu a primeira
        assertThat(notaRepository.count()).isEqualTo(1);
        NotaCliente nota = notaRepository.findById(new NotaClienteId(bia.getId(), ana.getId())).orElseThrow();
        assertThat(nota.getTexto()).isEqualTo("Cozinha pequena, levar fogareiro");
        assertThat(nota.getTextoOriginal()).isNull();
        mockMvc.perform(get("/clientes/" + ana.getId() + "/historico").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.nota.texto").value("Cozinha pequena, levar fogareiro"));

        gravarPelaApi(bia, ana, "   ").andExpect(status().isNoContent());
        assertThat(notaRepository.count()).isZero();
        mockMvc.perform(get("/clientes/" + ana.getId() + "/historico").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.nota").value(nullValue()));
        // apagar quando não há nota também é 204
        mockMvc.perform(put("/clientes/" + ana.getId() + "/nota").cookie(cookieDe(bia))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void notaComPalavraoEContatoMascaradaComOriginalGuardado() throws Exception {
        novoAgendamento(ana, bia, AGORA.plusDays(1), StatusAgendamento.CONFIRMADO);

        gravarPelaApi(bia, ana, "Cliente um palavrao, zap 21 98888-7777")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("Cliente um ***, zap ***"));

        NotaCliente nota = notaRepository.findById(new NotaClienteId(bia.getId(), ana.getId())).orElseThrow();
        assertThat(nota.getTexto()).isEqualTo("Cliente um ***, zap ***");
        assertThat(nota.getTextoOriginal()).isEqualTo("Cliente um palavrao, zap 21 98888-7777");
        mockMvc.perform(get("/clientes/" + ana.getId() + "/historico").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.nota.texto").value("Cliente um ***, zap ***"));
    }

    @Test
    void odioNaNotaBloqueiaOChefENaoGrava() throws Exception {
        novoAgendamento(ana, bia, AGORA.plusDays(1), StatusAgendamento.CONFIRMADO);
        gravarNota(bia, ana, "Nota anterior");

        gravarPelaApi(bia, ana, "que odiento").andExpect(status().isForbidden());

        assertThat(notaRepository.findById(new NotaClienteId(bia.getId(), ana.getId())).orElseThrow().getTexto())
                .isEqualTo("Nota anterior");
        assertThat(chefeRepository.findById(bia.getId()).orElseThrow().getBloqueadoEm()).isNotNull();
    }

    @Test
    void notaDeClienteNuncaAtendidoDa404SemModerar() throws Exception {
        novoAgendamento(ana, bia, AGORA.plusDays(1), StatusAgendamento.RECUSADO);

        // o acesso vem antes do filtro: nem o texto de ódio bloqueia quem não pode ver o cliente
        gravarPelaApi(bia, ana, "que odiento").andExpect(status().isNotFound());
        gravarPelaApi(caio, ana, "uma nota qualquer").andExpect(status().isNotFound());

        assertThat(notaRepository.count()).isZero();
        assertThat(chefeRepository.findById(bia.getId()).orElseThrow().getBloqueadoEm()).isNull();
    }

    @Test
    void notaAcimaDe2000Da400() throws Exception {
        novoAgendamento(ana, bia, AGORA.plusDays(1), StatusAgendamento.CONFIRMADO);

        gravarPelaApi(bia, ana, "a".repeat(2001)).andExpect(status().isBadRequest());
        gravarPelaApi(bia, ana, "a".repeat(2000)).andExpect(status().isOk());
    }

    @Test
    void notasSomemComAContaDoClienteEComADoChef() throws Exception {
        UserModel mia = novoCliente("Mia");
        novoAgendamento(ana, bia, AGORA.plusDays(1), StatusAgendamento.CONFIRMADO);
        novoAgendamento(mia, bia, AGORA.plusDays(1), StatusAgendamento.CONFIRMADO);
        novoAgendamento(mia, caio, AGORA.plusDays(2), StatusAgendamento.CONFIRMADO);
        gravarNota(bia, ana, "da Bia sobre a Ana");
        gravarNota(bia, mia, "da Bia sobre a Mia");
        gravarNota(caio, mia, "do Caio sobre a Mia");

        // conta do chef apagada (soft delete): as notas dele somem, as dos outros ficam
        mockMvc.perform(delete("/chefe/me").cookie(cookieDe(bia))).andExpect(status().isNoContent());
        assertThat(notaRepository.findAll()).extracting(NotaCliente::getTexto).containsExactly("do Caio sobre a Mia");

        // conta do cliente apagada: a nota do Caio sobre ela some junto
        agendamentoRepository.deleteAll();
        usuarioRepository.deleteById(mia.getId());
        assertThat(notaRepository.count()).isZero();
    }

    @Test
    void clienteNasRotasDeChefDa403() throws Exception {
        novoAgendamento(ana, bia, AGORA.plusDays(1), StatusAgendamento.CONFIRMADO);

        mockMvc.perform(get("/clientes").cookie(cookieDe(ana))).andExpect(status().isForbidden());
        mockMvc.perform(get("/clientes/" + ana.getId() + "/historico").cookie(cookieDe(ana))).andExpect(status().isForbidden());
        gravarPelaApi(ana, ana, "minha nota").andExpect(status().isForbidden());
        assertThat(notaRepository.count()).isZero();
    }

    // ─── apoio ───────────────────────────────────────────────────────────

    private long queries(MockHttpServletRequestBuilder pedido) throws Exception {
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
        mockMvc.perform(pedido).andExpect(status().isOk());
        return estatisticas.getPrepareStatementCount();
    }

    ResultActions gravarPelaApi(ChefeModel chefe, UserModel cliente, String texto) throws Exception {
        return mockMvc.perform(put("/clientes/" + cliente.getId() + "/nota").cookie(cookieDe(chefe))
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"" + texto + "\"}"));
    }

    ResultActions gravarPelaApi(UserModel quemQuer, UserModel cliente, String texto) throws Exception {
        return mockMvc.perform(put("/clientes/" + cliente.getId() + "/nota").cookie(cookieDe(quemQuer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"" + texto + "\"}"));
    }

    NotaCliente gravarNota(ChefeModel chefe, UserModel cliente, String texto) {
        NotaCliente nota = new NotaCliente();
        nota.setId(new NotaClienteId(chefe.getId(), cliente.getId()));
        nota.setChefe(chefe);
        nota.setCliente(cliente);
        nota.setTexto(texto);
        nota.setAtualizadaEm(LocalDateTime.now());
        return notaRepository.save(nota);
    }

    Agendamento novoAgendamento(UserModel cliente, ChefeModel chefe, LocalDateTime inicio, StatusAgendamento status,
                                RestricaoAgendamento... restricoes) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(novoEndereco(cliente));
        agendamento.setChefe(chefe);
        agendamento.setStatus(status);
        agendamento.setOcasiao(OcasiaoMenu.FAMILIA);
        agendamento.setDataHoraInicial(inicio);
        agendamento.aplicarMenu(menuDe(chefe), 2);
        agendamento.getRestricoes().addAll(Set.of(restricoes));
        return agendamentoRepository.save(agendamento);
    }

    private Menu menuDe(ChefeModel chefe) {
        return menusPorChefe.computeIfAbsent(chefe.getId(), id -> menus.ativo(chefe));
    }

    private final Map<UUID, Menu> menusPorChefe = new HashMap<>();

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
        usuario.setEmail(nome.toLowerCase().replace(' ', '-') + "-" + UUID.randomUUID() + "@x.com");
        usuario.setImageUrl("https://fotos/" + nome.toLowerCase().replace(' ', '-') + ".png");
        usuario.setEndereco(new ArrayList<>());
        return usuarioRepository.save(TelefoneDeTeste.verificado(usuario));
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
        return chefeRepository.save(TelefoneDeTeste.verificado(chefe));
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
