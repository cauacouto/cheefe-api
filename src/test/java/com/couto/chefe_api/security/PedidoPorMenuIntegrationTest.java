package com.couto.chefe_api.security;

import com.couto.chefe_api.FiltroDeTeste;
import com.couto.chefe_api.MenuDeTeste;
import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.domain.AgendaBloqueio;
import com.couto.chefe_api.domain.AgendaDiaFixo;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.Menu;
import com.couto.chefe_api.domain.RestricaoAgendamento;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.OcasiaoMenu;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoRestricao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendaBloqueioRepository;
import com.couto.chefe_api.repository.AgendaDiaFixoRepository;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.BloqueioContaRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.MenuRepository;
import com.couto.chefe_api.repository.PratosRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pedido por menu: o chef, o fim e o valor vêm do menu (que é copiado para o agendamento), com ocasião,
 * restrições alimentares e observações moderadas.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FiltroDeTeste.class)
class PedidoPorMenuIntegrationTest {

    static final LocalDateTime BASE = LocalDateTime.now().plusDays(10).withHour(19).withMinute(30).withSecond(0).withNano(0);
    static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired PratosRepository pratosRepository;
    @Autowired MenuRepository menuRepository;
    @Autowired AgendaBloqueioRepository bloqueioRepository;
    @Autowired AgendaDiaFixoRepository diaFixoRepository;
    @Autowired BloqueioContaRepository bloqueioContaRepository;
    @Autowired MenuDeTeste menus;
    @Autowired TransactionTemplate transacao;

    MockMvc mockMvc;
    ChefeModel bia;
    UserModel ana;
    long endereco;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bia = novoChefe("Bia");
        ana = novoCliente("Ana");
        endereco = novoEndereco(ana).getId();
    }

    @AfterEach
    void limparBanco() {
        agendamentoRepository.deleteAll();
        bloqueioContaRepository.deleteAll();
        bloqueioRepository.deleteAll();
        pratosRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    // ─── criação ─────────────────────────────────────────────────────────

    @Test
    void fimEValorVemDoMenu() throws Exception {
        Menu menu = menuDe(bia, "150.50", 2, 10);

        pedir(ana, menu.getId(), BASE, 8, "FAMILIA", null, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataHoraInicial").value(BASE.format(FORMATO)))
                .andExpect(jsonPath("$.dataHoraFinal").value(BASE.plusMinutes(120).format(FORMATO)))
                .andExpect(jsonPath("$.valorTotal").value(1204.00))
                .andExpect(jsonPath("$.chefe.id").value(bia.getId().toString()))
                .andExpect(jsonPath("$.menu.id").value(menu.getId()))
                .andExpect(jsonPath("$.menu.nome").value("Menu de teste"))
                .andExpect(jsonPath("$.menu.precoPorPessoa").value(150.50))
                .andExpect(jsonPath("$.menu.duracaoMinutos").value(120))
                .andExpect(jsonPath("$.menu.ingredientesInclusos").value(true))
                .andExpect(jsonPath("$.ocasiao").value("FAMILIA"))
                .andExpect(jsonPath("$.quantidadePessoas").value(8));
    }

    @Test
    void convidadosForaDoLimiteDa409() throws Exception {
        Menu menu = menuDe(bia, "100.00", 2, 10);

        pedir(ana, menu.getId(), BASE, 1, "FAMILIA", null, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este menu atende de 2 a 10 pessoas"));
        pedir(ana, menu.getId(), BASE, 11, "FAMILIA", null, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este menu atende de 2 a 10 pessoas"));
        assertThat(agendamentoRepository.count()).isZero();
    }

    @Test
    void menuPausadoOuChefNaoVisivelDa409() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);
        menu.setAtivo(false);
        menuRepository.save(menu);
        pedir(ana, menu.getId(), BASE, 2, "FAMILIA", null, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Menu indisponível"));

        menu.setAtivo(true);
        menuRepository.save(menu);
        bia.setDisponivel(false); // some da busca
        chefeRepository.save(bia);
        pedir(ana, menu.getId(), BASE, 2, "FAMILIA", null, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Menu indisponível"));
        assertThat(agendamentoRepository.count()).isZero();
    }

    @Test
    void menuInexistenteDa404() throws Exception {
        pedir(ana, 999_999L, BASE, 2, "FAMILIA", null, null).andExpect(status().isNotFound());
    }

    @Test
    void restricoesInvalidasDao400() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);

        // tipo repetido
        pedir(ana, menu.getId(), BASE, 4, "FAMILIA",
                "[{\"tipo\":\"VEGANO\",\"quantidade\":1},{\"tipo\":\"VEGANO\",\"quantidade\":2}]", null)
                .andExpect(status().isBadRequest());
        // quantidade 0
        pedir(ana, menu.getId(), BASE, 4, "FAMILIA", "[{\"tipo\":\"VEGANO\",\"quantidade\":0}]", null)
                .andExpect(status().isBadRequest());
        // quantidade acima do total de pessoas
        pedir(ana, menu.getId(), BASE, 4, "FAMILIA", "[{\"tipo\":\"VEGANO\",\"quantidade\":5}]", null)
                .andExpect(status().isBadRequest());
        // tipo desconhecido
        pedir(ana, menu.getId(), BASE, 4, "FAMILIA", "[{\"tipo\":\"PALEO\",\"quantidade\":1}]", null)
                .andExpect(status().isBadRequest());
        assertThat(agendamentoRepository.count()).isZero();
    }

    @Test
    void somaDasRestricoesPodePassarDoTotal() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);

        pedir(ana, menu.getId(), BASE, 4, "FAMILIA",
                "[{\"tipo\":\"VEGANO\",\"quantidade\":3},{\"tipo\":\"SEM_GLUTEN\",\"quantidade\":3}]", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restricoes", hasSize(2)))
                .andExpect(jsonPath("$.restricoes[0].tipo").value("VEGANO"))
                .andExpect(jsonPath("$.restricoes[0].quantidade").value(3))
                .andExpect(jsonPath("$.restricoes[1].tipo").value("SEM_GLUTEN"));
    }

    @Test
    void observacoesMascaradasEOriginalGuardado() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);

        String corpo = pedir(ana, menu.getId(), BASE, 2, "FAMILIA", null, "sem coentro 21 98888-7777")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.observacoes").value("sem coentro ***"))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("98888").doesNotContain("observacoesOriginal");
        Agendamento salvo = agendamentoRepository.findAll().get(0);
        assertThat(salvo.getObservacoes()).isEqualTo("sem coentro ***");
        assertThat(salvo.getObservacoesOriginal()).isEqualTo("sem coentro 21 98888-7777");

        // as listagens também não mostram o original
        String lista = mockMvc.perform(get("/agendamentos/meus").cookie(cookieDe(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].observacoes").value("sem coentro ***"))
                .andReturn().getResponse().getContentAsString();
        assertThat(lista).doesNotContain("98888").doesNotContain("observacoesOriginal");
        String recebidos = mockMvc.perform(get("/agendamentos/recebidos").cookie(cookieDe(bia))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(recebidos).doesNotContain("98888").doesNotContain("observacoesOriginal");
    }

    @Test
    void observacoesLimpasNaoGuardamOriginalEEmBrancoViramNulo() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);

        pedir(ana, menu.getId(), BASE, 2, "FAMILIA", null, "sem coentro").andExpect(status().isCreated());
        pedir(ana, menu.getId(), BASE.plusDays(1), 2, "FAMILIA", null, "   ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.observacoes").doesNotExist());

        assertThat(agendamentoRepository.findAll()).allSatisfy(a -> assertThat(a.getObservacoesOriginal()).isNull())
                .anyMatch(a -> a.getObservacoes() == null);
    }

    @Test
    void odioNasObservacoesBloqueiaENaoGrava() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);

        pedir(ana, menu.getId(), BASE, 2, "FAMILIA", null, "seu odiento").andExpect(status().isForbidden());

        assertThat(agendamentoRepository.count()).isZero();
        assertThat(usuarioRepository.findById(ana.getId()).orElseThrow().getBloqueadoEm()).isNotNull();
        assertThat(bloqueioContaRepository.findAll()).singleElement()
                .satisfies(b -> assertThat(b.getTexto()).isEqualTo("seu odiento"));
    }

    @Test
    void putDeQuemNaoEDonoDa404SemModerarNemBloquear() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);
        long id = idDe(pedir(ana, menu.getId(), BASE, 4, "FAMILIA", null, null).andExpect(status().isCreated()));
        UserModel caio = novoCliente("Caio");

        editar(caio, id, "{\"observacoes\":\"seu odiento\"}").andExpect(status().isNotFound());

        assertThat(usuarioRepository.findById(caio.getId()).orElseThrow().getBloqueadoEm()).isNull();
        assertThat(bloqueioContaRepository.count()).isZero();
        assertThat(agendamentoRepository.findById(id).orElseThrow().getObservacoes()).isNull();
    }

    @Test
    void ocasiaoDoPedidoNaoPrecisaSerDoMenu() throws Exception {
        Menu menu = menus.ativo(bia, new BigDecimal("100.00"), OcasiaoMenu.EMPRESA);

        pedir(ana, menu.getId(), BASE, 2, "ANIVERSARIO", null, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.ocasiao").value("ANIVERSARIO"));
    }

    @Test
    void editarMenuNaoAlteraAgendamentoExistente() throws Exception {
        Menu menu = menuDe(bia, "150.50", 2, 10);
        long id = idDe(pedir(ana, menu.getId(), BASE, 8, "FAMILIA", null, null).andExpect(status().isCreated()));
        String antes = lerDoCliente(id);

        long pratoId = pratosRepository.findAll().get(0).getId();
        String edicao = "{\"nome\":\"Menu novo\",\"precoPorPessoa\":999.00,\"minimoConvidados\":2,\"maximoConvidados\":10,"
                + "\"duracaoMinutos\":240,\"ingredientesInclusos\":false,\"ocasioes\":[\"FAMILIA\"],"
                + "\"itens\":[{\"pratoId\":" + pratoId + ",\"etapa\":\"PRINCIPAL\",\"ordem\":1}]}";
        mockMvc.perform(put("/menus/" + menu.getId()).cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content(edicao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precoPorPessoa").value(999.00));

        assertThat(lerDoCliente(id)).isEqualTo(antes);
    }

    // ─── edição ──────────────────────────────────────────────────────────

    @Test
    void putTrocaMenuDoMesmoChefRecalcula() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);
        Menu outro = menuDe(bia, "200.00", 1, 10);
        outro.setNome("Menu longo");
        outro.setDuracaoMinutos(180);
        menuRepository.save(outro);
        long id = idDe(pedir(ana, menu.getId(), BASE, 4, "FAMILIA", null, null).andExpect(status().isCreated()));

        editar(ana, id, "{\"menuId\":" + outro.getId() + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.menu.id").value(outro.getId()))
                .andExpect(jsonPath("$.menu.nome").value("Menu longo"))
                .andExpect(jsonPath("$.valorTotal").value(800.00))
                .andExpect(jsonPath("$.dataHoraFinal").value(BASE.plusMinutes(180).format(FORMATO)));
    }

    @Test
    void putTrocaMenuDeOutroChefDa409() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);
        ChefeModel caio = novoChefe("Caio");
        Menu doCaio = menuDe(caio, "100.00", 1, 10);
        long id = idDe(pedir(ana, menu.getId(), BASE, 4, "FAMILIA", null, null).andExpect(status().isCreated()));

        editar(ana, id, "{\"menuId\":" + doCaio.getId() + "}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Para trocar de chef, cancele e faça um novo pedido"));
        assertThat(agendamentoRepository.findById(id).orElseThrow().getMenu().getId()).isEqualTo(menu.getId());
    }

    @Test
    void putQuantidadeRecalculaValor() throws Exception {
        Menu menu = menuDe(bia, "100.50", 2, 10);
        long id = idDe(pedir(ana, menu.getId(), BASE, 4, "FAMILIA", null, null).andExpect(status().isCreated()));

        editar(ana, id, "{\"quantidadePessoas\":6}").andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadePessoas").value(6))
                .andExpect(jsonPath("$.valorTotal").value(603.00));
        // continua valendo o limite do menu
        editar(ana, id, "{\"quantidadePessoas\":11}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este menu atende de 2 a 10 pessoas"));
    }

    @Test
    void putComOsMesmosValoresNaoRecopiaNemDa409ComMenuRepreciadoEPausado() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);
        long id = idDe(pedir(ana, menu.getId(), BASE, 4, "FAMILIA", null, null).andExpect(status().isCreated()));
        // o formulário inteiro volta igual, mas o menu mudou de preço e foi pausado: o pedido não muda
        menu.setPrecoPorPessoa(new BigDecimal("300.00"));
        menu.setAtivo(false);
        menuRepository.save(menu);

        editar(ana, id, "{\"menuId\":" + menu.getId() + ",\"quantidadePessoas\":4}").andExpect(status().isOk())
                .andExpect(jsonPath("$.valorTotal").value(400.00))
                .andExpect(jsonPath("$.menu.precoPorPessoa").value(100.00));
        long outroEndereco = novoEndereco(ana).getId();
        editar(ana, id, "{\"enderecoId\":" + outroEndereco + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.valorTotal").value(400.00))
                .andExpect(jsonPath("$.menu.precoPorPessoa").value(100.00));
        assertThat(agendamentoRepository.findById(id).orElseThrow().getPrecoPorPessoa()).isEqualByComparingTo("100.00");
    }

    @Test
    void putComQuantidadeRealmenteTrocadaRecopiaOMenu() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);
        long id = idDe(pedir(ana, menu.getId(), BASE, 4, "FAMILIA", null, null).andExpect(status().isCreated()));
        menu.setPrecoPorPessoa(new BigDecimal("300.00"));
        menuRepository.save(menu);

        editar(ana, id, "{\"menuId\":" + menu.getId() + ",\"quantidadePessoas\":5}").andExpect(status().isOk())
                .andExpect(jsonPath("$.valorTotal").value(1500.00))
                .andExpect(jsonPath("$.menu.precoPorPessoa").value(300.00));
    }

    @Test
    void putHorarioMantemADuracaoDaCopia() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);
        long id = idDe(pedir(ana, menu.getId(), BASE, 4, "FAMILIA", null, null).andExpect(status().isCreated()));
        // o menu muda, mas o pedido segue com a duração que o cliente contratou
        menu.setDuracaoMinutos(360);
        menuRepository.save(menu);

        editar(ana, id, "{\"dataHoraInicial\":\"" + BASE.plusDays(1).format(FORMATO) + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.dataHoraInicial").value(BASE.plusDays(1).format(FORMATO)))
                .andExpect(jsonPath("$.dataHoraFinal").value(BASE.plusDays(1).plusMinutes(120).format(FORMATO)))
                .andExpect(jsonPath("$.valorTotal").value(400.00));
    }

    @Test
    void putAtualizaOcasiaoRestricoesEObservacoes() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);
        long id = idDe(pedir(ana, menu.getId(), BASE, 4, "FAMILIA",
                "[{\"tipo\":\"VEGANO\",\"quantidade\":1}]", "sem coentro 21 98888-7777").andExpect(status().isCreated()));

        editar(ana, id, "{\"ocasiao\":\"AMIGOS\",\"restricoes\":[{\"tipo\":\"ALERGIA_OVO\",\"quantidade\":2}],"
                + "\"observacoes\":\"sem pimenta\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.ocasiao").value("AMIGOS"))
                .andExpect(jsonPath("$.restricoes", hasSize(1)))
                .andExpect(jsonPath("$.restricoes[0].tipo").value("ALERGIA_OVO"))
                .andExpect(jsonPath("$.observacoes").value("sem pimenta"));
        assertThat(agendamentoRepository.findById(id).orElseThrow().getObservacoesOriginal()).isNull();

        editar(ana, id, "{\"restricoes\":[],\"observacoes\":\"\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.restricoes", hasSize(0)))
                .andExpect(jsonPath("$.observacoes").doesNotExist());
    }

    // ─── agenda do chef sobre o fim calculado ────────────────────────────

    @Test
    void fimCalculadoCaiEmBloqueioDa409() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);
        // o início (19:30) está livre; só o fim calculado (21:30) toca o bloqueio
        bloquear(bia, BASE.withHour(21).withMinute(0), BASE.withHour(23).withMinute(0));

        pedir(ana, menu.getId(), BASE, 2, "FAMILIA", null, null).andExpect(status().isConflict());
        assertThat(agendamentoRepository.count()).isZero();
    }

    @Test
    void fimCalculadoCaiNumDiaFixoDa409() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);
        LocalDateTime tarde = BASE.withHour(23).withMinute(0); // termina à 01:00 do dia seguinte
        diaFixoRepository.save(new AgendaDiaFixo(bia, tarde.plusDays(1).getDayOfWeek()));

        pedir(ana, menu.getId(), tarde, 2, "FAMILIA", null, null).andExpect(status().isConflict());
        assertThat(agendamentoRepository.count()).isZero();
    }

    @Test
    void fimCalculadoSobrepoeConfirmadoDa409() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);
        Agendamento confirmado = novoAgendamentoAntigo(novoCliente("Edu"), BASE.withHour(21).withMinute(0));
        confirmado.setStatus(StatusAgendamento.CONFIRMADO);
        agendamentoRepository.save(confirmado);

        pedir(ana, menu.getId(), BASE, 2, "FAMILIA", null, null).andExpect(status().isConflict());
    }

    @Test
    void putReduzQuantidadeAbaixoDaRestricaoGravadaDa400() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);
        long id = idDe(pedir(ana, menu.getId(), BASE, 6, "FAMILIA",
                "[{\"tipo\":\"VEGANO\",\"quantidade\":5}]", null).andExpect(status().isCreated()));

        editar(ana, id, "{\"quantidadePessoas\":4}").andExpect(status().isBadRequest());

        Agendamento salvo = agendamentoRepository.findById(id).orElseThrow();
        assertThat(salvo.getQuantidadePessoas()).isEqualTo(6);
    }

    // ─── agendamento de antes dos menus ──────────────────────────────────

    @Test
    void agendamentoAntigoSemMenuRespondeSemErroECicloFunciona() throws Exception {
        Agendamento antigo = novoAgendamentoAntigo(ana, BASE);

        mockMvc.perform(get("/agendamentos/meus").cookie(cookieDe(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(antigo.getId()))
                .andExpect(jsonPath("$.content[0].menu").doesNotExist())
                .andExpect(jsonPath("$.content[0].restricoes", hasSize(0)))
                .andExpect(jsonPath("$.content[0].valorTotal").value(200.00));
        mockMvc.perform(get("/agendamentos/recebidos").cookie(cookieDe(bia))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].menu").doesNotExist());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/agendamentos/" + antigo.getId() + "/confirmar").cookie(cookieDe(bia)))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/agendamentos/" + antigo.getId() + "/cancelar").cookie(cookieDe(ana)))
                .andExpect(status().isOk());
        assertThat(agendamentoRepository.findById(antigo.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.CANCELADO);
    }

    @Test
    void putEmAgendamentoAntigoDa409() throws Exception {
        Agendamento antigo = novoAgendamentoAntigo(ana, BASE);

        editar(ana, antigo.getId(), "{\"quantidadePessoas\":3}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Agendamento antigo: cancele e faça um novo pedido"));
        assertThat(agendamentoRepository.findById(antigo.getId()).orElseThrow().getQuantidadePessoas()).isEqualTo(2);
    }

    @Test
    void restricoesSaoGravadasEApagadasComOAgendamento() throws Exception {
        Menu menu = menuDe(bia, "100.00", 1, 10);
        long id = idDe(pedir(ana, menu.getId(), BASE, 4, "FAMILIA",
                "[{\"tipo\":\"VEGETARIANO\",\"quantidade\":2}]", null).andExpect(status().isCreated()));

        transacao.executeWithoutResult(s -> assertThat(agendamentoRepository.findById(id).orElseThrow().getRestricoes())
                .containsExactly(new RestricaoAgendamento(TipoRestricao.VEGETARIANO, 2)));
        agendamentoRepository.deleteAll();
        assertThat(agendamentoRepository.count()).isZero();
    }

    // ─── apoio ───────────────────────────────────────────────────────────

    ResultActions pedir(UserModel cliente, Long menuId, LocalDateTime inicio, int pessoas, String ocasiao,
                        String restricoes, String observacoes) throws Exception {
        StringBuilder corpo = new StringBuilder("{\"menuId\":" + menuId + ",\"dataHoraInicial\":\"" + inicio.format(FORMATO)
                + "\",\"enderecoId\":" + enderecoDe(cliente) + ",\"quantidadePessoas\":" + pessoas
                + ",\"ocasiao\":\"" + ocasiao + "\"");
        if (restricoes != null) {
            corpo.append(",\"restricoes\":").append(restricoes);
        }
        if (observacoes != null) {
            corpo.append(",\"observacoes\":").append(JSON.writeValueAsString(observacoes));
        }
        corpo.append("}");
        return mockMvc.perform(post("/agendamentos").cookie(cookieDe(cliente)).contentType(MediaType.APPLICATION_JSON)
                .content(corpo.toString()));
    }

    ResultActions editar(UserModel cliente, long id, String corpo) throws Exception {
        return mockMvc.perform(put("/agendamentos/" + id).cookie(cookieDe(cliente)).contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    String lerDoCliente(long id) throws Exception {
        String lista = mockMvc.perform(get("/agendamentos/meus").cookie(cookieDe(ana))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var linha : JSON.readTree(lista).get("content")) {
            if (linha.get("id").asLong() == id) {
                return linha.toString();
            }
        }
        throw new AssertionError("agendamento " + id + " fora da lista");
    }

    long enderecoDe(UserModel cliente) {
        return cliente.getId().equals(ana.getId()) ? endereco : novoEndereco(cliente).getId();
    }

    static long idDe(ResultActions resposta) throws Exception {
        return JSON.readTree(resposta.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    /** Menu ativo do chef com preço e faixa de convidados escolhidos (120 minutos). */
    Menu menuDe(ChefeModel chefe, String preco, int minimo, int maximo) {
        Menu menu = menus.ativo(chefe, new BigDecimal(preco));
        menu.setMinimoConvidados(minimo);
        menu.setMaximoConvidados(maximo);
        return menuRepository.save(menu);
    }

    void bloquear(ChefeModel chefe, LocalDateTime inicio, LocalDateTime fim) {
        AgendaBloqueio bloqueio = new AgendaBloqueio();
        bloqueio.setChefe(chefe);
        bloqueio.setInicio(inicio);
        bloqueio.setFim(fim);
        bloqueio.setCriadoEm(LocalDateTime.now());
        bloqueioRepository.save(bloqueio);
    }

    /** Agendamento gravado direto, no formato de antes dos menus: sem menu, cópia, ocasião nem observações. */
    Agendamento novoAgendamentoAntigo(UserModel cliente, LocalDateTime inicio) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(novoEndereco(cliente));
        agendamento.setChefe(bia);
        agendamento.setDataHoraInicial(inicio);
        agendamento.setDataHoraFinal(inicio.plusHours(2));
        agendamento.setQuantidadePessoas(2);
        agendamento.setValorTotal(new BigDecimal("200.00"));
        return agendamentoRepository.save(agendamento);
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
