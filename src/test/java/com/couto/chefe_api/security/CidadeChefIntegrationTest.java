package com.couto.chefe_api.security;

import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

/** Onde o chef atende: só na cidade de origem dele, e a busca filtra por cidade e especialidade. */
@SpringBootTest
@ActiveProfiles("test")
class CidadeChefIntegrationTest {

    static final LocalDateTime BASE = LocalDateTime.now().plusDays(10).withHour(18).withMinute(0).withSecond(0).withNano(0);
    static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired com.couto.chefe_api.repository.PratosRepository pratosRepository;
    @Autowired com.couto.chefe_api.MenuDeTeste menus;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void limparBanco() {
        agendamentoRepository.deleteAll();
        pratosRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    // ─── perfil ─────────────────────────────────────────────────────────

    @Test
    void semCidadeOPerfilFicaPendenteEOPatchCompleta() throws Exception {
        ChefeModel bia = novoChefe("Bia", null, null, Especialidades.BRASILEIRA);
        Cookie ana = cookieDe(novoCliente("Ana"));

        mockMvc.perform(get("/chefe/me").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.perfilCompleto").value(false))
                .andExpect(jsonPath("$.camposPendentes", contains("cidade", "estado")));
        mockMvc.perform(get("/chefe").cookie(ana)).andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(patch("/chefe/me").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cidade\":\"  São Paulo \",\"estado\":\"sp\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cidade").value("São Paulo"))
                .andExpect(jsonPath("$.estado").value("SP"));

        mockMvc.perform(get("/chefe/me").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.perfilCompleto").value(true))
                .andExpect(jsonPath("$.cidade").value("São Paulo"));
        mockMvc.perform(get("/chefe").cookie(ana))
                .andExpect(jsonPath("$.content[0].cidade").value("São Paulo"))
                .andExpect(jsonPath("$.content[0].estado").value("SP"));
    }

    @Test
    void cidadeOuUfInvalidaDa400() throws Exception {
        ChefeModel bia = novoChefe("Bia", "São Paulo", "SP", Especialidades.BRASILEIRA);

        for (String corpo : List.of("{\"estado\":\"São\"}", "{\"estado\":\"S\"}", "{\"cidade\":\"   \"}",
                "{\"cidade\":\"" + "x".repeat(101) + "\"}")) {
            mockMvc.perform(patch("/chefe/me").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── busca ──────────────────────────────────────────────────────────

    @Test
    void buscaPorCidadeIgnoraAcentoMaiusculasEEspacos() throws Exception {
        ChefeModel bia = novoChefe("Bia", "São Paulo", "SP", Especialidades.BRASILEIRA);
        ChefeModel caio = novoChefe("Caio", "Campinas", "SP", Especialidades.ITALIANA);
        novoChefe("Duda", "Belém", "PA", Especialidades.BRASILEIRA);
        Cookie ana = cookieDe(novoCliente("Ana"));

        for (String cidade : List.of("São Paulo", "sao paulo", "SÃO   PAULO", " Sao Paulo ")) {
            mockMvc.perform(get("/chefe").param("cidade", cidade).param("estado", "sp").cookie(ana))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].id", contains(bia.getId().toString())));
        }
        // só a UF: todos do estado
        mockMvc.perform(get("/chefe").param("estado", "SP").cookie(ana))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(bia.getId().toString(), caio.getId().toString())));
        // cidade sem UF é ambígua (há cidades com o mesmo nome em estados diferentes)
        mockMvc.perform(get("/chefe").param("cidade", "São Paulo").cookie(ana)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/chefe").param("cidade", "Recife").param("estado", "PE").cookie(ana))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void buscaPorEspecialidadeECombinadaComCidade() throws Exception {
        ChefeModel bia = novoChefe("Bia", "São Paulo", "SP", Especialidades.BRASILEIRA);
        ChefeModel caio = novoChefe("Caio", "Campinas", "SP", Especialidades.ITALIANA);
        ChefeModel duda = novoChefe("Duda", "Campinas", "SP", Especialidades.BRASILEIRA);
        Cookie ana = cookieDe(novoCliente("Ana"));

        mockMvc.perform(get("/chefe").param("especialidade", "ITALIANA").cookie(ana))
                .andExpect(jsonPath("$.content[*].id", contains(caio.getId().toString())));
        mockMvc.perform(get("/chefe").param("especialidade", "BRASILEIRA").param("cidade", "campinas").param("estado", "SP")
                        .cookie(ana))
                .andExpect(jsonPath("$.content[*].id", contains(duda.getId().toString())));
        mockMvc.perform(get("/chefe").param("especialidade", "BRASILEIRA").cookie(ana))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(bia.getId().toString(), duda.getId().toString())));
        mockMvc.perform(get("/chefe").param("especialidade", "PIZZA").cookie(ana)).andExpect(status().isBadRequest());
    }

    // ─── agendamento ────────────────────────────────────────────────────

    @Test
    void agendarSoNaCidadeDoChef() throws Exception {
        ChefeModel bia = novoChefe("Bia", "São Paulo", "SP", Especialidades.BRASILEIRA);
        ChefeModel caio = novoChefe("Caio", "Campinas", "SP", Especialidades.ITALIANA);
        UserModel ana = novoCliente("Ana");
        long emCampinas = endereco(ana, "Campinas", "SP").getId();
        long emSaoPauloMinusculo = endereco(ana, "sao paulo", "sp").getId();

        pedir(ana, emCampinas, bia).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Chef Bia não atende em Campinas"));
        pedir(ana, emCampinas, caio).andExpect(status().isCreated());
        // mesma cidade escrita de outro jeito
        pedir(ana, emSaoPauloMinusculo, bia).andExpect(status().isCreated());
    }

    @Test
    void mesmaCidadeEmOutraUfNaoServe() throws Exception {
        ChefeModel bia = novoChefe("Bia", "São Paulo", "SP", Especialidades.BRASILEIRA);
        UserModel ana = novoCliente("Ana");

        pedir(ana, endereco(ana, "São Paulo", "MG").getId(), bia).andExpect(status().isConflict());
    }

    @Test
    void editarParaEnderecoDeOutraCidadeDa409() throws Exception {
        ChefeModel bia = novoChefe("Bia", "São Paulo", "SP", Especialidades.BRASILEIRA);
        UserModel ana = novoCliente("Ana");
        long emSaoPaulo = endereco(ana, "São Paulo", "SP").getId();
        long emCampinas = endereco(ana, "Campinas", "SP").getId();
        long pedido = JSON.readTree(pedir(ana, emSaoPaulo, bia).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        editar(ana, pedido, "{\"enderecoId\":" + emCampinas + "}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Chef Bia não atende em Campinas"));
        // outro endereço na cidade do chef passa
        editar(ana, pedido, "{\"enderecoId\":" + endereco(ana, "São Paulo", "SP").getId() + "}").andExpect(status().isOk());
    }

    @Test
    void chefQueMudouDeCidadeNaoConfirmaPedidoAntigo() throws Exception {
        ChefeModel bia = novoChefe("Bia", "São Paulo", "SP", Especialidades.BRASILEIRA);
        UserModel ana = novoCliente("Ana");
        long pedido = JSON.readTree(pedir(ana, endereco(ana, "São Paulo", "SP").getId(), bia)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/chefe/me").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cidade\":\"Campinas\"}")).andExpect(status().isOk());

        mockMvc.perform(patch("/agendamentos/" + pedido + "/confirmar").cookie(cookieDe(bia)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Chef Bia não atende em São Paulo"));
        assertThat(agendamentoRepository.findById(pedido).orElseThrow().getStatus()).isEqualTo(StatusAgendamento.PENDENTE);
    }

    // ---------------------------------------------------------------------

    ResultActions pedir(UserModel cliente, long enderecoId, ChefeModel chefe) throws Exception {
        long menuId = menus.ativo(chefe).getId();
        return mockMvc.perform(post("/agendamentos").cookie(cookieDe(cliente)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuId\":" + menuId + ",\"dataHoraInicial\":\"" + BASE.format(FORMATO)
                        + "\",\"enderecoId\":" + enderecoId + ",\"quantidadePessoas\":2,\"ocasiao\":\"FAMILIA\"}"));
    }

    ResultActions editar(UserModel cliente, long agendamentoId, String corpo) throws Exception {
        return mockMvc.perform(put("/agendamentos/" + agendamentoId).cookie(cookieDe(cliente))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    EnderecoModel endereco(UserModel dono, String cidade, String estado) {
        EnderecoModel endereco = new EnderecoModel();
        endereco.setRua("Rua A");
        endereco.setNumero("1");
        endereco.setBairro("Centro");
        endereco.setCidade(cidade);
        endereco.setEstado(estado);
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
        return usuarioRepository.save(usuario);
    }

    ChefeModel novoChefe(String nome, String cidade, String estado, Especialidades especialidade) {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome(nome);
        chefe.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("100"));
        chefe.setEspecialidade(especialidade);
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        chefe.setCidade(cidade);
        chefe.setEstado(estado);
        return chefeRepository.save(chefe);
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
