package com.couto.chefe_api.security;

import com.couto.chefe_api.MenuDeTeste;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.Menu;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.OcasiaoMenu;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.MenuRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A busca olha os menus ativos do chef (ocasião, preço por pessoa) e as especialidades da lista dele. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class BuscaPorMenuIntegrationTest {

    static final LocalDateTime BASE = LocalDateTime.now().plusDays(10).withHour(18).withMinute(0).withSecond(0).withNano(0);

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired PratosRepository pratosRepository;
    @Autowired MenuRepository menuRepository;
    @Autowired MenuDeTeste menus;
    @Autowired EntityManagerFactory entityManagerFactory;

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

    // ─── filtros pelos menus ────────────────────────────────────────────

    @Test
    void filtraPorOcasiao() throws Exception {
        ChefeModel bia = novoChefe("Bia", Especialidades.BRASILEIRA);
        menus.ativo(bia, new BigDecimal("100.00"), OcasiaoMenu.ANIVERSARIO, OcasiaoMenu.FAMILIA);
        ChefeModel caio = novoChefe("Caio", Especialidades.ITALIANA);
        menus.ativo(caio, new BigDecimal("100.00"), OcasiaoMenu.EMPRESA);
        Cookie ana = cookieDe(novoCliente("Ana"));

        mockMvc.perform(get("/chefe").param("ocasiao", "ANIVERSARIO").cookie(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(bia.getId().toString())));
        mockMvc.perform(get("/chefe").param("ocasiao", "EMPRESA").cookie(ana))
                .andExpect(jsonPath("$.content[*].id", contains(caio.getId().toString())));
        mockMvc.perform(get("/chefe").param("ocasiao", "AMIGOS").cookie(ana))
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/chefe").cookie(ana))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(bia.getId().toString(), caio.getId().toString())));
    }

    @Test
    void ocasiaoInvalidaDa400() throws Exception {
        mockMvc.perform(get("/chefe").param("ocasiao", "BAILE").cookie(cookieDe(novoCliente("Ana"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void faixaDePrecoValeSobreOPrecoPorPessoa() throws Exception {
        ChefeModel barato = novoChefe("Barato", Especialidades.BRASILEIRA);
        menus.ativo(barato, new BigDecimal("80.00"));
        ChefeModel medio = novoChefe("Medio", Especialidades.BRASILEIRA);
        menus.ativo(medio, new BigDecimal("150.00"));
        ChefeModel caro = novoChefe("Caro", Especialidades.BRASILEIRA);
        menus.ativo(caro, new BigDecimal("400.00"));
        Cookie ana = cookieDe(novoCliente("Ana"));

        mockMvc.perform(get("/chefe").param("valorMinimo", "100").param("valorMaximo", "200").cookie(ana))
                .andExpect(jsonPath("$.content[*].id", contains(medio.getId().toString())));
        mockMvc.perform(get("/chefe").param("valorMinimo", "150").cookie(ana))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(medio.getId().toString(), caro.getId().toString())));
        mockMvc.perform(get("/chefe").param("valorMaximo", "80").cookie(ana))
                .andExpect(jsonPath("$.content[*].id", contains(barato.getId().toString())));
    }

    @Test
    void ocasiaoEFaixaValemParaOMesmoMenu() throws Exception {
        // o chef tem um menu de aniversário caro e um menu de empresa barato: nenhum é "aniversário até 100"
        ChefeModel bia = novoChefe("Bia", Especialidades.BRASILEIRA);
        menus.ativo(bia, new BigDecimal("300.00"), OcasiaoMenu.ANIVERSARIO);
        menus.ativo(bia, new BigDecimal("60.00"), OcasiaoMenu.EMPRESA);
        // este tem um único menu que cumpre os dois filtros
        ChefeModel caio = novoChefe("Caio", Especialidades.BRASILEIRA);
        menus.ativo(caio, new BigDecimal("90.00"), OcasiaoMenu.ANIVERSARIO);
        Cookie ana = cookieDe(novoCliente("Ana"));

        mockMvc.perform(get("/chefe").param("ocasiao", "ANIVERSARIO").param("valorMaximo", "100").cookie(ana))
                .andExpect(jsonPath("$.content[*].id", contains(caio.getId().toString())));
        mockMvc.perform(get("/chefe").param("ocasiao", "ANIVERSARIO").param("valorMaximo", "400").cookie(ana))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(bia.getId().toString(), caio.getId().toString())));
    }

    @Test
    void chefSoComMenuPausadoSomeDaBusca() throws Exception {
        ChefeModel bia = novoChefe("Bia", Especialidades.BRASILEIRA);
        pausar(menus.ativo(bia));
        ChefeModel caio = novoChefe("Caio", Especialidades.BRASILEIRA);
        menus.ativo(caio);
        Cookie ana = cookieDe(novoCliente("Ana"));

        mockMvc.perform(get("/chefe").cookie(ana))
                .andExpect(jsonPath("$.content[*].id", contains(caio.getId().toString())));
        mockMvc.perform(get("/chefe/destaque").cookie(ana))
                .andExpect(jsonPath("$.content[*].id", contains(caio.getId().toString())));
    }

    @Test
    void chefSemMenuTemMenuEmCamposPendentes() throws Exception {
        ChefeModel bia = novoChefe("Bia", Especialidades.BRASILEIRA);

        mockMvc.perform(get("/chefe/me").cookie(cookieDe(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfilCompleto").value(false))
                .andExpect(jsonPath("$.camposPendentes", contains("menu")))
                .andExpect(jsonPath("$.precoAPartirDe").doesNotExist());

        menus.ativo(bia, new BigDecimal("120.00"));

        mockMvc.perform(get("/chefe/me").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.perfilCompleto").value(true))
                .andExpect(jsonPath("$.camposPendentes").isEmpty())
                .andExpect(jsonPath("$.precoAPartirDe").value(120.0))
                .andExpect(jsonPath("$.especialidades", contains("BRASILEIRA")));
    }

    // ─── ordenação por preço ────────────────────────────────────────────

    @Test
    void ordenaPorPrecoAPartirDeNasDuasDirecoes() throws Exception {
        ChefeModel caro = novoChefe("Caro", Especialidades.BRASILEIRA);
        menus.ativo(caro, new BigDecimal("300.00"));
        ChefeModel barato = novoChefe("Barato", Especialidades.BRASILEIRA);
        menus.ativo(barato, new BigDecimal("90.00"));
        // o menor preço entre os menus ativos é o que conta: 150, não 500
        ChefeModel medio = novoChefe("Medio", Especialidades.BRASILEIRA);
        menus.ativo(medio, new BigDecimal("500.00"));
        menus.ativo(medio, new BigDecimal("150.00"));
        Cookie ana = cookieDe(novoCliente("Ana"));

        mockMvc.perform(get("/chefe").param("sort", "precoAPartirDe,asc").cookie(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nome", contains("Barato", "Medio", "Caro")))
                .andExpect(jsonPath("$.content[*].precoAPartirDe", contains(90.0, 150.0, 300.0)))
                .andExpect(jsonPath("$.page.totalElements").value(3));
        mockMvc.perform(get("/chefe").param("sort", "precoAPartirDe,desc").cookie(ana))
                .andExpect(jsonPath("$.content[*].nome", contains("Caro", "Medio", "Barato")));
        mockMvc.perform(get("/chefe").param("sort", "precoAPartirDe,asc").param("size", "2").param("page", "1").cookie(ana))
                .andExpect(jsonPath("$.content[*].nome", contains("Caro")))
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void precoAPartirDeIgnoraMenuPausado() throws Exception {
        ChefeModel bia = novoChefe("Bia", Especialidades.BRASILEIRA);
        pausar(menus.ativo(bia, new BigDecimal("50.00")));
        menus.ativo(bia, new BigDecimal("200.00"));
        ChefeModel caio = novoChefe("Caio", Especialidades.BRASILEIRA);
        menus.ativo(caio, new BigDecimal("120.00"));
        Cookie ana = cookieDe(novoCliente("Ana"));

        // se o pausado contasse, Bia (50) viria antes de Caio (120)
        mockMvc.perform(get("/chefe").param("sort", "precoAPartirDe,asc").cookie(ana))
                .andExpect(jsonPath("$.content[*].nome", contains("Caio", "Bia")))
                .andExpect(jsonPath("$.content[*].precoAPartirDe", contains(120.0, 200.0)));
        // e a faixa também não enxerga o menu pausado
        mockMvc.perform(get("/chefe").param("valorMaximo", "60").cookie(ana))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void notaMediaVenceSobrePrecoAPartirDe() throws Exception {
        ChefeModel barato = novoChefe("Barato", Especialidades.BRASILEIRA);
        menus.ativo(barato, new BigDecimal("90.00"));
        ChefeModel bem = novoChefe("Bem", Especialidades.BRASILEIRA);
        menus.ativo(bem, new BigDecimal("300.00"));
        bem.setSomaNotas(25);
        bem.setTotalAvaliacoes(5);
        chefeRepository.save(bem);

        mockMvc.perform(get("/chefe").param("sort", "notaMedia", "precoAPartirDe,asc").cookie(cookieDe(novoCliente("Ana"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nome", contains("Bem", "Barato")));
    }

    // ─── especialidades ─────────────────────────────────────────────────

    @Test
    void especialidadeFiltraQuemTemNaLista() throws Exception {
        ChefeModel bia = novoChefe("Bia", Especialidades.BRASILEIRA, Especialidades.CONFEITARIA);
        menus.ativo(bia);
        ChefeModel caio = novoChefe("Caio", Especialidades.ITALIANA);
        menus.ativo(caio);
        Cookie ana = cookieDe(novoCliente("Ana"));

        mockMvc.perform(get("/chefe").param("especialidade", "CONFEITARIA").cookie(ana))
                .andExpect(jsonPath("$.content[*].id", contains(bia.getId().toString())))
                .andExpect(jsonPath("$.content[0].especialidades", containsInAnyOrder("BRASILEIRA", "CONFEITARIA")));
        mockMvc.perform(get("/chefe").param("especialidade", "ITALIANA").cookie(ana))
                .andExpect(jsonPath("$.content[*].id", contains(caio.getId().toString())));
        mockMvc.perform(get("/chefe").param("especialidade", "JAPONESA").cookie(ana))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void patchComZeroOuQuatroEspecialidadesDa400() throws Exception {
        ChefeModel bia = novoChefe("Bia", Especialidades.BRASILEIRA);

        for (String corpo : List.of("{\"especialidades\":[]}",
                "{\"especialidades\":[\"FRANCESA\",\"ITALIANA\",\"JAPONESA\",\"CHINESA\"]}")) {
            mockMvc.perform(patch("/chefe/me").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/chefe/me").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.especialidades", contains("BRASILEIRA")));
    }

    @Test
    void patchComEspecialidadeRepetidaDa400() throws Exception {
        ChefeModel bia = novoChefe("Bia", Especialidades.BRASILEIRA);

        mockMvc.perform(patch("/chefe/me").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"especialidades\":[\"FRANCESA\",\"FRANCESA\"]}"))
                .andExpect(status().isBadRequest());

        // e a troca válida substitui a lista inteira
        mockMvc.perform(patch("/chefe/me").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"especialidades\":[\"FRANCESA\",\"ITALIANA\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidades", containsInAnyOrder("FRANCESA", "ITALIANA")));
        mockMvc.perform(get("/chefe/me").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.especialidades", containsInAnyOrder("FRANCESA", "ITALIANA")));
    }

    // ─── destaque ───────────────────────────────────────────────────────

    @Test
    void destaqueNaoContaRecusadoCanceladoNemExpirado() throws Exception {
        UserModel ana = novoCliente("Ana");
        // quem recusa continua no agendamento: se esses contassem, Recusa (3) passaria de Aceita (2)
        ChefeModel recusa = novoChefe("Recusa", Especialidades.BRASILEIRA);
        menus.ativo(recusa);
        novoAgendamento(ana, recusa, StatusAgendamento.RECUSADO, 1);
        novoAgendamento(ana, recusa, StatusAgendamento.CANCELADO, 2);
        novoAgendamento(ana, recusa, StatusAgendamento.EXPIRADO, 3);
        ChefeModel aceita = novoChefe("Aceita", Especialidades.BRASILEIRA);
        menus.ativo(aceita);
        novoAgendamento(ana, aceita, StatusAgendamento.CONFIRMADO, 4);
        novoAgendamento(ana, aceita, StatusAgendamento.PENDENTE, 5);
        ChefeModel concluiu = novoChefe("Concluiu", Especialidades.BRASILEIRA);
        menus.ativo(concluiu);
        novoAgendamento(ana, concluiu, StatusAgendamento.CONCLUIDO, 6);

        mockMvc.perform(get("/chefe/destaque").cookie(cookieDe(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nome", contains("Aceita", "Concluiu", "Recusa")))
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    // ─── consultas ──────────────────────────────────────────────────────

    @Test
    void buscaNaoFazUmaConsultaPorChef() throws Exception {
        Cookie ana = cookieDe(novoCliente("Ana"));
        for (int i = 0; i < 2; i++) {
            chefComMenus("Chef" + i, i);
        }
        long comDois = queries(get("/chefe").cookie(ana));
        long comDoisPorPreco = queries(get("/chefe").param("sort", "precoAPartirDe,asc").cookie(ana));
        long destaqueComDois = queries(get("/chefe/destaque").cookie(ana));
        for (int i = 2; i < 10; i++) {
            chefComMenus("Chef" + i, i);
        }

        assertThat(queries(get("/chefe").cookie(ana))).isEqualTo(comDois);
        assertThat(queries(get("/chefe").param("sort", "precoAPartirDe,asc").cookie(ana))).isEqualTo(comDoisPorPreco);
        assertThat(queries(get("/chefe/destaque").cookie(ana))).isEqualTo(destaqueComDois);
        // e a página traz o que a lista precisa
        mockMvc.perform(get("/chefe").param("size", "50").cookie(ana))
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.content[*].especialidades.length()", hasItem(2)))
                .andExpect(jsonPath("$.content[*].precoAPartirDe").isNotEmpty());
    }

    private long queries(MockHttpServletRequestBuilder pedido) throws Exception {
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
        mockMvc.perform(pedido).andExpect(status().isOk());
        return estatisticas.getPrepareStatementCount();
    }

    // ---------------------------------------------------------------------

    private void chefComMenus(String nome, int i) {
        ChefeModel chefe = novoChefe(nome, Especialidades.BRASILEIRA, Especialidades.values()[i % 5 + 1]);
        menus.ativo(chefe, new BigDecimal(100 + i));
        menus.ativo(chefe, new BigDecimal(200 + i), OcasiaoMenu.FAMILIA);
    }

    private void pausar(Menu menu) {
        menu.setAtivo(false);
        menuRepository.save(menu);
    }

    Agendamento novoAgendamento(UserModel cliente, ChefeModel chefe, StatusAgendamento status, int dia) {
        EnderecoModel endereco = new EnderecoModel();
        endereco.setRua("Rua A");
        endereco.setNumero("1");
        endereco.setBairro("Centro");
        endereco.setCidade("São Paulo");
        endereco.setEstado("SP");
        endereco.setCep("01001-000");
        endereco.setTipoDeResidencia(TipoDeResidencia.CASA);
        endereco.setUsuario(cliente);

        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(enderecoRepository.save(endereco));
        agendamento.setChefe(chefe);
        agendamento.setDataHoraInicial(BASE.plusDays(dia));
        agendamento.aplicarMenu(menuRepository.findByChefe_IdAndAtivoTrueOrderByIdAsc(chefe.getId()).get(0), 2);
        agendamento.setOcasiao(OcasiaoMenu.FAMILIA);
        agendamento.setStatus(status);
        return agendamentoRepository.save(agendamento);
    }

    UserModel novoCliente(String nome) {
        UserModel usuario = new UserModel();
        usuario.setNome(nome);
        usuario.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        usuario.setEndereco(new ArrayList<>());
        return usuarioRepository.save(usuario);
    }

    /** Perfil completo, mas sem menu: quem quer aparecer na busca cadastra um com {@code menus.ativo}. */
    ChefeModel novoChefe(String nome, Especialidades... especialidades) {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome(nome);
        chefe.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.getEspecialidades().addAll(List.of(especialidades));
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
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
