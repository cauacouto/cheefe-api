package com.couto.chefe_api.security;

import com.couto.chefe_api.TelefoneDeTeste;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

/** Ciclo do agendamento pela API: cancelar, recusar, desistir e concluir, com histórico (spec ciclo-agendamento). */
@SpringBootTest
@ActiveProfiles("test")
class CicloAgendamentoIntegrationTest {

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

    @Test
    void clienteCancelaPendenteComMotivoEDescricao() throws Exception {
        UserModel ana = novoCliente("Ana");
        Agendamento a = novoAgendamento(ana, BASE, novoChefe("Bia"));

        acao(cookieDe(ana), a, "cancelar", "{\"motivo\":\"MUDANCA_DE_PLANOS\",\"descricao\":\"  viagem  \"}")
                .andExpect(status().isOk());

        assertThat(statusDe(a)).isEqualTo(StatusAgendamento.CANCELADO);
        AgendamentoHistorico linha = ultima(a);
        assertThat(linha.getAcao()).isEqualTo(AcaoAgendamento.CANCELADO);
        assertThat(linha.getAutorId()).isEqualTo(ana.getId());
        assertThat(linha.getAutorTipo()).isEqualTo(TipoUsuario.CLIENTE);
        assertThat(linha.getMotivo()).isEqualTo("MUDANCA_DE_PLANOS");
        assertThat(linha.getDescricao()).isEqualTo("viagem");
    }

    @Test
    void cancelarSemCorpoComCorpoVazioOuDescricaoEmBranco() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        for (String corpo : new String[]{null, "{}", "{\"descricao\":\"   \"}"}) {
            Agendamento a = novoAgendamento(ana, BASE.plusDays(historicoRepository.count()), bia);
            acao(cookieDe(ana), a, "cancelar", corpo).andExpect(status().isOk());
            AgendamentoHistorico linha = ultima(a);
            assertThat(linha.getMotivo()).as("corpo %s", corpo).isNull();
            assertThat(linha.getDescricao()).as("corpo %s", corpo).isNull();
        }
    }

    @Test
    void cancelarDuasVezesDa409SemNovoHistorico() throws Exception {
        UserModel ana = novoCliente("Ana");
        Agendamento a = novoAgendamento(ana, BASE, novoChefe("Bia"));
        acao(cookieDe(ana), a, "cancelar", null).andExpect(status().isOk());
        long linhas = historicoRepository.count();

        acao(cookieDe(ana), a, "cancelar", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("CANCELADO")));
        assertThat(historicoRepository.count()).isEqualTo(linhas);
    }

    @Test
    void motivoDoOutroLadoOuDescricaoLongaDa400() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        Agendamento a = novoAgendamento(ana, BASE, bia);

        acao(cookieDe(ana), a, "cancelar", "{\"motivo\":\"AGENDA_INDISPONIVEL\"}").andExpect(status().isBadRequest());
        acao(cookieDe(bia), a, "recusar", "{\"motivo\":\"VALOR\"}").andExpect(status().isBadRequest());
        acao(cookieDe(ana), a, "cancelar", "{\"descricao\":\"" + "x".repeat(301) + "\"}").andExpect(status().isBadRequest());
        assertThat(statusDe(a)).isEqualTo(StatusAgendamento.PENDENTE);
    }

    @Test
    void recusaParcialEDepoisOChefQueSaiuRecebe404() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        Agendamento a = novoAgendamento(ana, BASE, bia, caio);

        acao(cookieDe(bia), a, "recusar", "{\"motivo\":\"LOCAL_FORA_DA_AREA\"}").andExpect(status().isOk());

        Agendamento depois = agendamentoRepository.findById(a.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusAgendamento.PENDENTE);
        assertThat(nomesDosChefes(a)).containsExactly("Caio");
        assertThat(depois.getValorTotal()).isEqualByComparingTo("200.00"); // só o Caio: R$ 100/h x 2h
        assertThat(ultima(a).getAcao()).isEqualTo(AcaoAgendamento.RECUSADO);
        assertThat(ultima(a).getAutorId()).isEqualTo(bia.getId());

        acao(cookieDe(bia), a, "recusar", null).andExpect(status().isNotFound());
        acao(cookieDe(bia), a, "concluir", null).andExpect(status().isNotFound());
        mockMvc.perform(get("/agendamentos/" + a.getId() + "/historico").cookie(cookieDe(bia))).andExpect(status().isNotFound());
    }

    @Test
    void desistenciaDoUltimoChefCancelaELiberaOHorario() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        Agendamento a = novoAgendamento(novoCliente("Ana"), BASE, bia);
        confirmar(a);

        acao(cookieDe(bia), a, "desistir", "{\"motivo\":\"IMPREVISTO\"}").andExpect(status().isOk());

        assertThat(statusDe(a)).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(ultima(a).getAcao()).isEqualTo(AcaoAgendamento.DESISTENCIA);
        Agendamento outro = novoAgendamento(novoCliente("Edu"), BASE, bia);
        mockMvc.perform(patch("/agendamentos/" + outro.getId() + "/confirmar").cookie(cookieDe(bia))).andExpect(status().isOk());
    }

    @Test
    void chefConcluiDepoisDoInicio() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        Agendamento a = novoAgendamento(novoCliente("Ana"), LocalDateTime.now().minusMinutes(30), bia);
        confirmar(a);

        acao(cookieDe(bia), a, "concluir", null).andExpect(status().isOk());

        assertThat(statusDe(a)).isEqualTo(StatusAgendamento.CONCLUIDO);
        assertThat(ultima(a).getAcao()).isEqualTo(AcaoAgendamento.CONCLUIDO);
    }

    @Test
    void donoEPapel() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        Agendamento a = novoAgendamento(ana, BASE, bia);
        ChefeModel banido = novoChefe("Dani");
        Agendamento doBanido = novoAgendamento(ana, BASE.plusDays(1), banido);
        banido.setAtivo(false);
        chefeRepository.save(banido);

        acao(cookieDe(novoCliente("Edu")), a, "cancelar", null).andExpect(status().isNotFound());
        acao(cookieDe(novoChefe("Caio")), a, "recusar", null).andExpect(status().isNotFound());
        acao(cookieDe(bia), a, "cancelar", null).andExpect(status().isForbidden());
        acao(cookieDe(ana), a, "recusar", null).andExpect(status().isForbidden());
        acao(cookieDe(banido), doBanido, "recusar", null).andExpect(status().isForbidden());
        assertThat(statusDe(a)).isEqualTo(StatusAgendamento.PENDENTE);
    }

    @Test
    void criarEditarEConfirmarRegistramHistorico() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        long id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(mockMvc.perform(post("/agendamentos")
                        .cookie(cookieDe(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataHoraInicial\":\"" + BASE.format(FORMATO) + "\",\"dataHoraFinal\":\""
                                + BASE.plusHours(2).format(FORMATO) + "\",\"enderecoId\":" + novoEndereco(ana).getId()
                                + ",\"chefeIds\":[\"" + bia.getId() + "\"],\"quantidadePessoas\":2}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(put("/agendamentos/" + id).cookie(cookieDe(ana)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidadePessoas\":4}")).andExpect(status().isOk());
        mockMvc.perform(patch("/agendamentos/" + id + "/confirmar").cookie(cookieDe(bia))).andExpect(status().isOk());

        assertThat(historicoRepository.findByAgendamento_IdOrderByOcorridoEmAscIdAsc(id))
                .extracting(AgendamentoHistorico::getAcao, AgendamentoHistorico::getAutorTipo)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(AcaoAgendamento.CRIADO, TipoUsuario.CLIENTE),
                        org.assertj.core.groups.Tuple.tuple(AcaoAgendamento.EDITADO, TipoUsuario.CLIENTE),
                        org.assertj.core.groups.Tuple.tuple(AcaoAgendamento.CONFIRMADO, TipoUsuario.CHEFE));
    }

    @Test
    void recusaChegaComoNotificacaoParaOCliente() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        Agendamento a = novoAgendamento(ana, BASE, bia);

        acao(cookieDe(bia), a, "recusar", "{\"motivo\":\"AGENDA_INDISPONIVEL\"}").andExpect(status().isOk());

        mockMvc.perform(get("/notificacoes").cookie(cookieDe(ana)))
                .andExpect(jsonPath("$.content[0].evento").value("AGENDAMENTO_RECUSADO"))
                .andExpect(jsonPath("$.content[0].referenciaId").value(String.valueOf(a.getId())))
                .andExpect(jsonPath("$.content[0].mensagem").value(org.hamcrest.Matchers.endsWith(
                        "foi recusado: Não tenho agenda nesse horário")));
    }

    @Test
    void historicoEmOrdemComAutorEMotivo() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        Agendamento a = novoAgendamento(ana, BASE, bia, caio);
        acao(cookieDe(bia), a, "recusar", "{\"motivo\":\"NUMERO_DE_PESSOAS\",\"descricao\":\"só até 10\"}")
                .andExpect(status().isOk());
        acao(cookieDe(ana), a, "cancelar", "{\"motivo\":\"OUTRO\"}").andExpect(status().isOk());

        for (Cookie quem : new Cookie[]{cookieDe(ana), cookieDe(caio)}) {
            mockMvc.perform(get("/agendamentos/" + a.getId() + "/historico").cookie(quem))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].acao").value("RECUSADO"))
                    .andExpect(jsonPath("$[0].autor.nome").value("Bia"))
                    .andExpect(jsonPath("$[0].autor.tipo").value("CHEFE"))
                    .andExpect(jsonPath("$[0].motivo.codigo").value("NUMERO_DE_PESSOAS"))
                    .andExpect(jsonPath("$[0].motivo.rotulo").value("Não atendo essa quantidade de pessoas"))
                    .andExpect(jsonPath("$[0].descricao").value("só até 10"))
                    .andExpect(jsonPath("$[0].ocorridoEm").isNotEmpty())
                    .andExpect(jsonPath("$[1].acao").value("CANCELADO"))
                    .andExpect(jsonPath("$[1].autor.nome").value("Ana"))
                    .andExpect(jsonPath("$[1].motivo.rotulo").value("Outro motivo"))
                    .andExpect(jsonPath("$[1].descricao").doesNotExist());
        }
    }

    @Test
    void historicoDeOutroDa404() throws Exception {
        Agendamento a = novoAgendamento(novoCliente("Ana"), BASE, novoChefe("Bia"));

        mockMvc.perform(get("/agendamentos/" + a.getId() + "/historico").cookie(cookieDe(novoCliente("Edu"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/agendamentos/" + a.getId() + "/historico").cookie(cookieDe(novoChefe("Caio"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void doisCancelamentosAoMesmoTempoUmPassaSemHistoricoNemAvisoDuplicado() throws Exception {
        UserModel ana = novoCliente("Ana");
        Agendamento a = novoAgendamento(ana, BASE, novoChefe("Bia"));
        Cookie cliente = cookieDe(ana);

        List<Integer> status = emParalelo(List.of(
                () -> acao(cliente, a, "cancelar", null).andReturn().getResponse().getStatus(),
                () -> acao(cliente, a, "cancelar", null).andReturn().getResponse().getStatus()));

        assertThat(status).containsExactlyInAnyOrder(200, 409);
        assertThat(historicoRepository.findByAgendamento_IdOrderByOcorridoEmAscIdAsc(a.getId()))
                .filteredOn(l -> l.getAcao() == AcaoAgendamento.CANCELADO).hasSize(1);
        assertThat(notificacaoRepository.findAll()).filteredOn(n -> n.getEvento().name().equals("AGENDAMENTO_CANCELADO")).hasSize(1);
    }

    @Test
    void cancelarEConfirmarAoMesmoTempoTerminaConsistente() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        Agendamento a = novoAgendamento(ana, BASE, bia);
        Cookie cliente = cookieDe(ana);
        Cookie chef = cookieDe(bia);

        List<Integer> status = emParalelo(List.of(
                () -> acao(cliente, a, "cancelar", null).andReturn().getResponse().getStatus(),
                () -> mockMvc.perform(patch("/agendamentos/" + a.getId() + "/confirmar").cookie(chef))
                        .andReturn().getResponse().getStatus()));

        // as duas podem passar em sequência (confirmado com mais de 24h pode ser cancelado), mas o status
        // final tem de ser o da última transição registrada, nunca um "cancelado" sobrescrito
        List<AgendamentoHistorico> linhas = historicoRepository.findByAgendamento_IdOrderByOcorridoEmAscIdAsc(a.getId());
        AcaoAgendamento ultima = linhas.get(linhas.size() - 1).getAcao();
        assertThat(statusDe(a).name()).isEqualTo(ultima.name());
        assertThat(status).doesNotContain(500);
    }

    @Test
    void doisChefsRecusandoAoMesmoTempoViraRecusado() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        Agendamento a = novoAgendamento(novoCliente("Ana"), BASE, bia, caio);
        Cookie deBia = cookieDe(bia);
        Cookie deCaio = cookieDe(caio);

        emParalelo(List.of(
                () -> acao(deBia, a, "recusar", null).andReturn().getResponse().getStatus(),
                () -> acao(deCaio, a, "recusar", null).andReturn().getResponse().getStatus()));

        assertThat(statusDe(a)).isEqualTo(StatusAgendamento.RECUSADO);
        assertThat(nomesDosChefes(a)).isEmpty();
    }

    @Test
    void contaApagadaLevaOAvisoDeCancelamentoDoChefEOHistorico() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        Agendamento a = novoAgendamento(ana, BASE, bia);
        acao(cookieDe(ana), a, "cancelar", "{\"descricao\":\"texto pessoal da Ana\"}").andExpect(status().isOk());
        assertThat(notificacaoRepository.countByDestinatarioIdAndLidaFalse(bia.getId())).isEqualTo(1);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/usuario/me")
                .cookie(cookieDe(ana))).andExpect(status().isNoContent());

        // LGPD: o aviso do chef cita o nome e o texto da Ana
        assertThat(notificacaoRepository.countByDestinatarioIdAndLidaFalse(bia.getId())).isZero();
        assertThat(historicoRepository.findByAgendamento_IdOrderByOcorridoEmAscIdAsc(a.getId())).isEmpty();
    }

    private ResultActions acao(Cookie quem, Agendamento agendamento, String acao, String corpo) throws Exception {
        var pedido = patch("/agendamentos/" + agendamento.getId() + "/" + acao).cookie(quem);
        if (corpo != null) {
            pedido = pedido.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mockMvc.perform(pedido);
    }

    private StatusAgendamento statusDe(Agendamento agendamento) {
        return agendamentoRepository.findById(agendamento.getId()).orElseThrow().getStatus();
    }

    private AgendamentoHistorico ultima(Agendamento agendamento) {
        List<AgendamentoHistorico> linhas = historicoRepository.findByAgendamento_IdOrderByOcorridoEmAscIdAsc(agendamento.getId());
        assertThat(linhas).isNotEmpty();
        return linhas.get(linhas.size() - 1);
    }

    private List<String> nomesDosChefes(Agendamento agendamento) {
        return chefeRepository.findAll().stream()
                .filter(c -> agendamentoRepository.findRecebidos(c.getId(), org.springframework.data.domain.PageRequest.of(0, 50))
                        .stream().anyMatch(x -> x.getId().equals(agendamento.getId())))
                .map(ChefeModel::getNome).toList();
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

    Agendamento novoAgendamento(UserModel cliente, LocalDateTime inicio, ChefeModel... chefes) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(novoEndereco(cliente));
        agendamento.setChefes(new ArrayList<>(List.of(chefes)));
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
        return usuarioRepository.save(TelefoneDeTeste.verificado(usuario));
    }

    ChefeModel novoChefe(String nome) {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome(nome);
        chefe.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("100"));
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
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
