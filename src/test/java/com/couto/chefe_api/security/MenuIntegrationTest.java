package com.couto.chefe_api.security;

import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Menu;
import com.couto.chefe_api.domain.Pratos;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.MenuRepository;
import com.couto.chefe_api.repository.PratosRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.MenuDeTeste;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Menus do chef: cadastro com regras, limite de 20, pausar/ativar e a página pública. */
@SpringBootTest
@ActiveProfiles("test")
class MenuIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired PratosRepository pratosRepository;
    @Autowired MenuRepository menuRepository;
    @Autowired org.springframework.transaction.support.TransactionTemplate transacao;
    @Autowired MenuDeTeste menuDeTeste;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void limparBanco() {
        pratosRepository.deleteAll(); // os itens saem junto (ON DELETE CASCADE)
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll(); // os menus saem junto (ON DELETE CASCADE)
    }

    @Test
    void criaMenuComItensOrdenadosPorEtapa() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        Pratos sobremesa = novoPrato(bia, "Pudim");
        Pratos principal2 = novoPrato(bia, "Risoto");
        Pratos principal1 = novoPrato(bia, "Moqueca");
        Pratos couvert = novoPrato(bia, "Pão");

        String corpo = menu(base()
                .com("itens", List.of(
                        item(sobremesa, "SOBREMESA", 1),
                        item(principal2, "PRINCIPAL", 2),
                        item(principal1, "PRINCIPAL", 1),
                        item(couvert, "COUVERT", 1))));
        ResultActions resposta = mockMvc.perform(post("/menus").cookie(cookieDe(bia))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Jantar a dois"))
                .andExpect(jsonPath("$.precoPorPessoa").value(250.5))
                .andExpect(jsonPath("$.minimoConvidados").value(2))
                .andExpect(jsonPath("$.maximoConvidados").value(10))
                .andExpect(jsonPath("$.duracaoMinutos").value(180))
                .andExpect(jsonPath("$.ingredientesInclusos").value(true))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.ocasioes.length()").value(2))
                .andExpect(jsonPath("$.itens.length()").value(4))
                .andExpect(jsonPath("$.itens[0].nomePrato").value("Pão"))
                .andExpect(jsonPath("$.itens[0].etapa").value("COUVERT"))
                .andExpect(jsonPath("$.itens[0].descricao").value("Prato de teste"))
                .andExpect(jsonPath("$.itens[1].nomePrato").value("Moqueca"))
                .andExpect(jsonPath("$.itens[2].nomePrato").value("Risoto"))
                .andExpect(jsonPath("$.itens[3].nomePrato").value("Pudim"));

        JsonNode criado = JSON.readTree(resposta.andReturn().getResponse().getContentAsString());
        assertThat(menuRepository.findById(criado.get("id").asLong())).isPresent();

        // editar substitui itens e ocasiões
        String edicao = menu(base().com("nome", "Jantar a dois v2").com("ocasioes", List.of("FAMILIA"))
                .com("itens", List.of(item(principal1, "ENTRADA", 1))));
        mockMvc.perform(put("/menus/" + criado.get("id").asLong()).cookie(cookieDe(bia))
                        .contentType(MediaType.APPLICATION_JSON).content(edicao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Jantar a dois v2"))
                .andExpect(jsonPath("$.ocasioes.length()").value(1))
                .andExpect(jsonPath("$.ocasioes[0]").value("FAMILIA"))
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].etapa").value("ENTRADA"));
    }

    @Test
    void editarAcrescentandoEDepoisTrocandoPratosMantemOsItensCertos() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        Pratos um = novoPrato(bia, "Moqueca");
        Pratos dois = novoPrato(bia, "Risoto");
        Pratos tres = novoPrato(bia, "Pudim");
        long id = JSON.readTree(mockMvc.perform(post("/menus").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content(menu(base().com("itens", List.of(item(um, "PRINCIPAL", 1))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        // acrescenta um prato e mantém o que já estava
        mockMvc.perform(put("/menus/" + id).cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content(menu(base().com("itens", List.of(item(um, "PRINCIPAL", 1), item(dois, "SOBREMESA", 1))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.itens[0].nomePrato").value("Moqueca"))
                .andExpect(jsonPath("$.itens[1].nomePrato").value("Risoto"));
        assertThat(pratosDoMenu(id)).containsExactlyInAnyOrder(um.getId(), dois.getId());

        // tira o primeiro e acrescenta outro
        mockMvc.perform(put("/menus/" + id).cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content(menu(base().com("itens", List.of(item(dois, "SOBREMESA", 1), item(tres, "ENTRADA", 1))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(2));
        assertThat(pratosDoMenu(id)).containsExactlyInAnyOrder(dois.getId(), tres.getId());
    }

    @Test
    void cadaRegraInvalidaDa400() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        Pratos prato = novoPrato(bia, "Moqueca");
        String uma = "x";

        enviarInvalido(bia, prato, b -> b.com("nome", ""));
        enviarInvalido(bia, prato, b -> b.com("nome", uma.repeat(101)));
        enviarInvalido(bia, prato, b -> b.com("descricao", uma.repeat(1001)));
        enviarInvalido(bia, prato, b -> b.com("precoPorPessoa", 0));
        enviarInvalido(bia, prato, b -> b.com("minimoConvidados", 0));
        enviarInvalido(bia, prato, b -> b.com("minimoConvidados", 51).com("maximoConvidados", 51));
        enviarInvalido(bia, prato, b -> b.com("minimoConvidados", 5).com("maximoConvidados", 4));
        enviarInvalido(bia, prato, b -> b.com("maximoConvidados", 51));
        enviarInvalido(bia, prato, b -> b.com("duracaoMinutos", 59));
        enviarInvalido(bia, prato, b -> b.com("duracaoMinutos", 721));
        enviarInvalido(bia, prato, b -> b.com("duracaoMinutos", 75));
        enviarInvalido(bia, prato, b -> b.com("ocasioes", List.of()));
        enviarInvalido(bia, prato, b -> b.com("ocasioes", List.of("FAMILIA", "FAMILIA")));
        enviarInvalido(bia, prato, b -> b.com("itens", List.of()));
        enviarInvalido(bia, prato, b -> b.com("itens", mutavel(16, prato)));
        assertThat(menuRepository.count()).isZero();

        // 15 itens (o máximo) passa
        List<Object> quinze = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            quinze.add(item(novoPrato(bia, "Prato " + i), "PRINCIPAL", i));
        }
        mockMvc.perform(post("/menus").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content(menu(base().com("itens", quinze))))
                .andExpect(status().isCreated());
    }

    @Test
    void pratoDeOutroChefDa400() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        Pratos docaio = novoPrato(novoChefe("Caio"), "Feijoada");

        mockMvc.perform(post("/menus").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content(menu(base().com("itens", List.of(item(docaio, "PRINCIPAL", 1))))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/menus").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content(menu(base().com("itens", List.of(Map.of("pratoId", 999999,
                                "etapa", "PRINCIPAL", "ordem", 1))))))
                .andExpect(status().isBadRequest());
        assertThat(menuRepository.count()).isZero();
    }

    @Test
    void pratoRepetidoDa400() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        Pratos prato = novoPrato(bia, "Moqueca");

        mockMvc.perform(post("/menus").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content(menu(base().com("itens", List.of(item(prato, "ENTRADA", 1), item(prato, "PRINCIPAL", 2))))))
                .andExpect(status().isBadRequest());
        assertThat(menuRepository.count()).isZero();
    }

    @Test
    void contatoNoNomeOuDescricaoDa400ComCodigo() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        Pratos prato = novoPrato(bia, "Moqueca");

        for (var ajuste : List.<Consumer<Corpo>>of(
                b -> b.com("nome", "Menu zap 11 98765-4321"),
                b -> b.com("descricao", "Fale comigo em bia@exemplo.com"))) {
            Corpo corpo = base();
            corpo.com("itens", List.of(item(prato, "PRINCIPAL", 1)));
            ajuste.accept(corpo);
            mockMvc.perform(post("/menus").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                            .content(menu(corpo)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("CONTATO_NAO_PERMITIDO"))
                    .andExpect(jsonPath("$.detail", endsWith("no menu")));
        }
        assertThat(menuRepository.count()).isZero();
    }

    @Test
    void vinteEUmMenusDa409ContandoPausados() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        Pratos prato = novoPrato(bia, "Moqueca");
        String corpo = menu(base().com("itens", List.of(item(prato, "PRINCIPAL", 1))));

        long primeiro = 0;
        for (int i = 0; i < 20; i++) {
            ResultActions criado = mockMvc.perform(post("/menus").cookie(cookieDe(bia))
                    .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isCreated());
            if (i == 0) {
                primeiro = JSON.readTree(criado.andReturn().getResponse().getContentAsString()).get("id").asLong();
            }
        }
        mockMvc.perform(patch("/menus/" + primeiro + "/pausar").cookie(cookieDe(bia))).andExpect(status().isOk());

        mockMvc.perform(post("/menus").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict());
        assertThat(menuRepository.countByChefe_Id(bia.getId())).isEqualTo(20);

        // o limite é por chef
        ChefeModel caio = novoChefe("Caio");
        mockMvc.perform(post("/menus").cookie(cookieDe(caio)).contentType(MediaType.APPLICATION_JSON)
                        .content(menu(base().com("itens", List.of(item(novoPrato(caio, "Feijoada"), "PRINCIPAL", 1))))))
                .andExpect(status().isCreated());
    }

    @Test
    void pausarSomeDaPaginaPublicaEAtivarVolta() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        Menu menu = menuDeTeste.ativo(bia);
        // o segundo menu mantém a Bia visível: sem nenhum menu ativo ela sai da busca e a página pública dá 404
        menuDeTeste.ativo(bia);
        menuDeTeste.ativo(caio);
        Cookie cliente = cookieDe(novoCliente());

        mockMvc.perform(get("/chefe/" + bia.getId() + "/menus").cookie(cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(menu.getId()))
                .andExpect(jsonPath("$[0].itens[0].nomePrato").value("Prato do menu"));
        // quem é chef também consulta
        mockMvc.perform(get("/chefe/" + bia.getId() + "/menus").cookie(cookieDe(caio)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(patch("/menus/" + menu.getId() + "/pausar").cookie(cookieDe(bia)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(get("/chefe/" + bia.getId() + "/menus").cookie(cliente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(patch("/menus/" + menu.getId() + "/ativar").cookie(cookieDe(bia)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true));
        mockMvc.perform(get("/chefe/" + bia.getId() + "/menus").cookie(cliente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void editarOuPausarMenuDeOutroChefDa404() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        Menu daBia = menuDeTeste.ativo(bia);
        Pratos prato = novoPrato(caio, "Feijoada");

        mockMvc.perform(put("/menus/" + daBia.getId()).cookie(cookieDe(caio)).contentType(MediaType.APPLICATION_JSON)
                        .content(menu(base().com("itens", List.of(item(prato, "PRINCIPAL", 1))))))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/menus/" + daBia.getId() + "/pausar").cookie(cookieDe(caio))).andExpect(status().isNotFound());
        mockMvc.perform(patch("/menus/" + daBia.getId() + "/ativar").cookie(cookieDe(caio))).andExpect(status().isNotFound());
        mockMvc.perform(patch("/menus/999999/pausar").cookie(cookieDe(caio))).andExpect(status().isNotFound());
        assertThat(menuRepository.findById(daBia.getId()).orElseThrow().isAtivo()).isTrue();
    }

    @Test
    void chefInativoDa403() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        Menu menu = menuDeTeste.ativo(bia);
        Pratos prato = novoPrato(bia, "Moqueca");
        String corpo = menu(base().com("itens", List.of(item(prato, "PRINCIPAL", 1))));
        bia.setAtivo(false);
        chefeRepository.save(bia);
        Cookie cookie = cookieDe(bia);

        mockMvc.perform(post("/menus").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/menus/" + menu.getId()).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/menus/" + menu.getId() + "/pausar").cookie(cookie)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/menus/" + menu.getId() + "/ativar").cookie(cookie)).andExpect(status().isForbidden());
        mockMvc.perform(get("/menus/meus").cookie(cookie)).andExpect(status().isForbidden());
    }

    @Test
    void paginaPublicaDeChefNaoVisivelDa404() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        menuDeTeste.ativo(bia);
        Cookie cliente = cookieDe(novoCliente());

        bia.setDisponivel(false); // pausado: some da busca
        chefeRepository.save(bia);
        mockMvc.perform(get("/chefe/" + bia.getId() + "/menus").cookie(cliente)).andExpect(status().isNotFound());
        mockMvc.perform(get("/chefe/" + UUID.randomUUID() + "/menus").cookie(cliente)).andExpect(status().isNotFound());
        mockMvc.perform(get("/chefe/" + bia.getId() + "/menus")).andExpect(status().isUnauthorized());
    }

    @Test
    void clienteEmRotaDeChefDa403() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        Menu menu = menuDeTeste.ativo(bia);
        Cookie cliente = cookieDe(novoCliente());
        String corpo = menu(base().com("itens", List.of(item(novoPrato(bia, "Moqueca"), "PRINCIPAL", 1))));

        mockMvc.perform(post("/menus").cookie(cliente).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/menus/" + menu.getId()).cookie(cliente).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/menus/" + menu.getId() + "/pausar").cookie(cliente)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/menus/" + menu.getId() + "/ativar").cookie(cliente)).andExpect(status().isForbidden());
        mockMvc.perform(get("/menus/meus").cookie(cliente)).andExpect(status().isForbidden());
    }

    @Test
    void meusTrazOsPausados() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        Menu ativo = menuDeTeste.ativo(bia);
        Menu pausado = menuDeTeste.ativo(bia, new BigDecimal("80.00"));
        menuDeTeste.ativo(caio);
        pausado.setAtivo(false);
        menuRepository.save(pausado);

        mockMvc.perform(get("/menus/meus").cookie(cookieDe(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(ativo.getId()))
                .andExpect(jsonPath("$[1].id").value(pausado.getId()))
                .andExpect(jsonPath("$[1].ativo").value(false));
    }

    // ─── apoio ───────────────────────────────────────────────────────────

    /** Corpo do menu válido; os testes trocam o campo que querem quebrar. */
    static class Corpo extends LinkedHashMap<String, Object> {
        Corpo com(String campo, Object valor) {
            super.put(campo, valor);
            return this;
        }
    }

    static Corpo base() {
        return new Corpo().com("nome", "Jantar a dois").com("descricao", "Um jantar completo")
                .com("precoPorPessoa", 250.5).com("minimoConvidados", 2).com("maximoConvidados", 10)
                .com("duracaoMinutos", 180).com("ingredientesInclusos", true)
                .com("ocasioes", List.of("JANTAR_ROMANTICO", "ANIVERSARIO"));
    }

    static String menu(Corpo corpo) {
        return JSON.writeValueAsString(corpo);
    }

    static Map<String, Object> item(Pratos prato, String etapa, int ordem) {
        return Map.of("pratoId", prato.getId(), "etapa", etapa, "ordem", ordem);
    }

    static List<Object> mutavel(int quantidade, Pratos prato) {
        List<Object> itens = new ArrayList<>();
        for (int i = 1; i <= quantidade; i++) {
            itens.add(item(prato, "PRINCIPAL", i));
        }
        return itens;
    }

    /** Menu válido com um único campo (ou par de campos) quebrado deve dar 400. */
    void enviarInvalido(ChefeModel chefe, Pratos prato, Consumer<Corpo> quebra) throws Exception {
        Corpo corpo = base();
        corpo.com("itens", List.of(item(prato, "PRINCIPAL", 1)));
        quebra.accept(corpo);
        mockMvc.perform(post("/menus").cookie(cookieDe(chefe)).contentType(MediaType.APPLICATION_JSON)
                        .content(menu(corpo)))
                .andExpect(status().isBadRequest());
    }

    List<Integer> pratosDoMenu(long id) {
        return transacao.execute(t -> menuRepository.findById(id).orElseThrow().getItens().stream()
                .map(i -> i.getPrato().getId()).toList());
    }

    Pratos novoPrato(ChefeModel chefe, String nome) {
        Pratos prato = new Pratos();
        prato.setNomePrato(nome);
        prato.setDescricao("Prato de teste");
        prato.setChefeModel(chefe);
        return pratosRepository.save(prato);
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

    UserModel novoCliente() {
        UserModel usuario = new UserModel();
        usuario.setNome("Ana");
        usuario.setEmail("ana-" + UUID.randomUUID() + "@x.com");
        return usuarioRepository.save(TelefoneDeTeste.verificado(usuario));
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
