package com.couto.chefe_api.Security;

import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.domin.Pratos;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.AgendamentoRepository;
import com.couto.chefe_api.repositorys.EndercoRepository;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.PatrosRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regras de acesso pela cadeia de segurança real: papel por rota (403) e dono do recurso (404).
 */
@SpringBootTest
@ActiveProfiles("test")
class AutorizacaoIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ChefeRepository chefeRepository;
    @Autowired
    private PatrosRepository pratosRepository;
    @Autowired
    private EndercoRepository enderecoRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /**
     * O H2 é compartilhado por todos os @SpringBootTest do contexto: apaga na ordem das FKs
     * para não vazar dados para as outras classes de teste.
     */
    @AfterEach
    void limparBanco() {
        agendamentoRepository.deleteAll();
        pratosRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void chefNaoAcessaRotasDeCliente() throws Exception {
        Cookie chef = cookieDe(novoChefe());

        mockMvc.perform(get("/usuario/me").cookie(chef)).andExpect(status().isForbidden());
        mockMvc.perform(post("/agendamentos").cookie(chef).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/agendamentos/meus").cookie(chef)).andExpect(status().isForbidden());
    }

    @Test
    void clienteNaoAcessaRotasDeChef() throws Exception {
        Cookie cliente = cookieDe(novoCliente());

        mockMvc.perform(patch("/chefe/me").cookie(cliente).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/pratos/meus").cookie(cliente)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/agendamentos/1/confirmar").cookie(cliente)).andExpect(status().isForbidden());
        mockMvc.perform(get("/agendamentos/recebidos").cookie(cliente)).andExpect(status().isForbidden());
    }

    @Test
    void semCookieDa401() throws Exception {
        mockMvc.perform(get("/usuario/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void clienteVeSoOsProprioDados() throws Exception {
        UserModel ana = novoCliente();

        mockMvc.perform(get("/usuario/me").cookie(cookieDe(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ana.getEmail()));
    }

    @Test
    void rotasAntigasPorIdNaoExistem() throws Exception {
        Cookie ana = cookieDe(novoCliente());
        UserModel bia = novoCliente();

        mockMvc.perform(get("/usuario/" + bia.getId()).cookie(ana)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/usuario/" + bia.getId()).cookie(ana)).andExpect(status().isNotFound());
        assertThat(usuarioRepository.findById(bia.getId())).isPresent();
    }

    @Test
    void listagemDeUsuariosNaoExiste() throws Exception {
        String corpo = mockMvc.perform(get("/usuario").cookie(cookieDe(novoCliente())))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("cliente-");
    }

    @Test
    void putIgnoraEmailNoCorpo() throws Exception {
        UserModel ana = novoCliente();
        String json = """
                {"nome":"Ana","email":"outro@x.com","telefone":"11999999999",
                 "endereco":[{"rua":"Rua A","numero":"1","cep":"01001-000","cidade":"SP","bairro":"Centro",
                              "estado":"SP","tipoDeResidencia":"CASA"}]}
                """;

        mockMvc.perform(put("/usuario/me").cookie(cookieDe(ana)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        assertThat(usuarioRepository.findById(ana.getId()).orElseThrow().getEmail()).isEqualTo(ana.getEmail());
    }

    @Test
    void deleteMeApagaEExpiraCookie() throws Exception {
        UserModel ana = novoCliente();

        mockMvc.perform(delete("/usuario/me").cookie(cookieDe(ana)))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                        .anyMatch(c -> c.startsWith(AuthCookies.ACCESS_TOKEN + "=") && c.contains("Max-Age=0")));

        assertThat(usuarioRepository.findById(ana.getId())).isEmpty();
    }

    @Test
    void tokenDeContaApagadaDa404() throws Exception {
        UserModel ana = novoCliente();
        Cookie cookie = cookieDe(ana);
        usuarioRepository.deleteById(ana.getId());

        mockMvc.perform(get("/usuario/me").cookie(cookie)).andExpect(status().isNotFound());
    }

    @Test
    void chefEditaSoOProprioPerfil() throws Exception {
        ChefeModel x = novoChefe();
        ChefeModel y = novoChefe();

        mockMvc.perform(patch("/chefe/me").cookie(cookieDe(x)).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Novo\"}"))
                .andExpect(status().isOk());

        assertThat(chefeRepository.findById(x.getId()).orElseThrow().getNome()).isEqualTo("Novo");
        assertThat(chefeRepository.findById(y.getId()).orElseThrow().getNome()).isEqualTo("Chef");
    }

    @Test
    void rotaAntigaDeChefPorIdNaoExiste() throws Exception {
        ChefeModel x = novoChefe();
        ChefeModel y = novoChefe();

        mockMvc.perform(patch("/chefe/" + y.getId()).cookie(cookieDe(x)).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Hacked\"}"))
                .andExpect(status().isNotFound());

        assertThat(chefeRepository.findById(y.getId()).orElseThrow().getNome()).isEqualTo("Chef");
    }

    @Test
    void clienteNaoEditaChef() throws Exception {
        mockMvc.perform(patch("/chefe/me").cookie(cookieDe(novoCliente())).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteChefMeInativaEExpiraCookie() throws Exception {
        ChefeModel x = novoChefe();

        mockMvc.perform(delete("/chefe/me").cookie(cookieDe(x)))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                        .anyMatch(c -> c.startsWith(AuthCookies.ACCESS_TOKEN + "=") && c.contains("Max-Age=0")));

        assertThat(chefeRepository.findById(x.getId()).orElseThrow().getAtivo()).isFalse();
    }

    @Test
    void chefNaoEditaPratoDeOutroChef() throws Exception {
        ChefeModel x = novoChefe();
        Pratos pratoDeY = novoPrato(novoChefe(), "Moqueca");

        mockMvc.perform(put("/pratos/" + pratoDeY.getId()).cookie(cookieDe(x))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nomePrato\":\"Hacked\",\"descricao\":\"x\"}"))
                .andExpect(status().isNotFound());

        assertThat(pratosRepository.findById(pratoDeY.getId()).orElseThrow().getNomePrato()).isEqualTo("Moqueca");
    }

    @Test
    void meusPratosListaSoOsDoChef() throws Exception {
        ChefeModel x = novoChefe();
        novoPrato(x, "Prato de X");
        novoPrato(novoChefe(), "Prato de Y");

        mockMvc.perform(get("/pratos/meus").cookie(cookieDe(x)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nomePrato").value("Prato de X"));
    }

    @Test
    void putDePratoIgnoraImageUrlNoCorpo() throws Exception {
        ChefeModel x = novoChefe();
        Pratos prato = novoPrato(x, "Moqueca");
        prato.setImageUrl("http://localhost:9000/teste/pratos/ok.png");
        pratosRepository.save(prato);

        mockMvc.perform(put("/pratos/" + prato.getId()).cookie(cookieDe(x)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomePrato\":\"Moqueca\",\"descricao\":\"x\",\"imageUrl\":\"https://evil.example/x.png\"}"))
                .andExpect(status().isOk());

        assertThat(pratosRepository.findById(prato.getId()).orElseThrow().getImageUrl())
                .isEqualTo("http://localhost:9000/teste/pratos/ok.png");
    }

    Pratos novoPrato(ChefeModel chefe, String nome) {
        Pratos prato = new Pratos();
        prato.setNomePrato(nome);
        prato.setDescricao("descrição");
        prato.setChefeModel(chefe);
        return pratosRepository.save(prato);
    }

    @Test
    void clienteNaoAgendaComEnderecoDeOutro() throws Exception {
        UserModel ana = novoCliente();
        EnderecoModel enderecoDaBia = novoEndereco(novoCliente());

        mockMvc.perform(post("/agendamentos").cookie(cookieDe(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonAgendamento(enderecoDaBia.getId(), novoChefe(), "")))
                .andExpect(status().isNotFound());
    }

    @Test
    void campoDeIdentidadeNoCorpoEhIgnorado() throws Exception {
        UserModel ana = novoCliente();
        UserModel bia = novoCliente();
        EnderecoModel enderecoDaAna = novoEndereco(ana);
        String extras = ",\"email\":\"" + bia.getEmail() + "\",\"usuarioId\":\"" + bia.getId() + "\"";

        mockMvc.perform(post("/agendamentos").cookie(cookieDe(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonAgendamento(enderecoDaAna.getId(), novoChefe(), extras)))
                .andExpect(status().isCreated());

        assertThat(agendamentoRepository.findAllByUsuario_Id(ana.getId())).hasSize(1);
        assertThat(agendamentoRepository.findAllByUsuario_Id(bia.getId())).isEmpty();
    }

    @Test
    void clienteNaoEditaAgendamentoDeOutro() throws Exception {
        UserModel bia = novoCliente();
        Agendamento daBia = novoAgendamento(bia, novoChefe());

        mockMvc.perform(put("/agendamentos/" + daBia.getId()).cookie(cookieDe(novoCliente()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantidadePessoas\":99}"))
                .andExpect(status().isNotFound());

        assertThat(agendamentoRepository.findById(daBia.getId()).orElseThrow().getQuantidadePessoas()).isEqualTo(2);
    }

    @Test
    void meusListaSoOsDoCliente() throws Exception {
        UserModel ana = novoCliente();
        Agendamento daAna = novoAgendamento(ana, novoChefe());
        novoAgendamento(novoCliente(), novoChefe());

        mockMvc.perform(get("/agendamentos/meus").cookie(cookieDe(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(daAna.getId()))
                .andExpect(jsonPath("$[0].dataHora").isNotEmpty());
    }

    @Test
    void clienteNaoConfirma() throws Exception {
        UserModel ana = novoCliente();
        Agendamento agendamento = novoAgendamento(ana, novoChefe());

        mockMvc.perform(patch("/agendamentos/" + agendamento.getId() + "/confirmar").cookie(cookieDe(ana)))
                .andExpect(status().isForbidden());
    }

    @Test
    void chefForaDoAgendamentoNaoConfirma() throws Exception {
        Agendamento agendamento = novoAgendamento(novoCliente(), novoChefe());

        mockMvc.perform(patch("/agendamentos/" + agendamento.getId() + "/confirmar").cookie(cookieDe(novoChefe())))
                .andExpect(status().isNotFound());
    }

    @Test
    void chefDoAgendamentoConfirmaUmaVez() throws Exception {
        ChefeModel x = novoChefe();
        Agendamento agendamento = novoAgendamento(novoCliente(), x);

        mockMvc.perform(patch("/agendamentos/" + agendamento.getId() + "/confirmar").cookie(cookieDe(x)))
                .andExpect(status().isOk());
        assertThat(agendamentoRepository.findById(agendamento.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.CONFIRMADO);

        mockMvc.perform(patch("/agendamentos/" + agendamento.getId() + "/confirmar").cookie(cookieDe(x)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Só é possível confirmar agendamentos pendentes (status atual: CONFIRMADO)"));
    }

    @Test
    void recebidosListaSoOsDoChef() throws Exception {
        ChefeModel x = novoChefe();
        Agendamento deX = novoAgendamento(novoCliente(), x);
        novoAgendamento(novoCliente(), novoChefe());

        mockMvc.perform(get("/agendamentos/recebidos").cookie(cookieDe(x)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(deX.getId()))
                .andExpect(jsonPath("$[0].dataHora").isNotEmpty());
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

    Agendamento novoAgendamento(UserModel cliente, ChefeModel chefe) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(novoEndereco(cliente));
        agendamento.setChefes(new ArrayList<>(List.of(chefe)));
        agendamento.setDataHoraInicial(LocalDateTime.now().plusDays(10).withHour(18).withMinute(0).withSecond(0).withNano(0));
        agendamento.setDataHoraFinal(LocalDateTime.now().plusDays(10).withHour(20).withMinute(0).withSecond(0).withNano(0));
        agendamento.setQuantidadePessoas(2);
        agendamento.setValorTotal(new BigDecimal("200.00"));
        return agendamentoRepository.save(agendamento);
    }

    private static String jsonAgendamento(Long enderecoId, ChefeModel chefe, String extras) {
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        LocalDateTime dia = LocalDateTime.now().plusDays(10).withHour(0).withMinute(0);
        return "{\"dataHoraInicial\":\"" + dia.withHour(18).format(formato) + "\","
                + "\"dataHoraFinal\":\"" + dia.withHour(21).format(formato) + "\","
                + "\"enderecoId\":" + enderecoId + ","
                + "\"chefeIds\":[\"" + chefe.getId() + "\"],"
                + "\"quantidadePessoas\":2" + extras + "}";
    }

    @Test
    void chefApagadoNaoOperaComTokenAntigo() throws Exception {
        ChefeModel x = novoChefe();
        Cookie tokenAntigo = cookieDe(x);
        Pratos prato = novoPrato(x, "Moqueca");
        Agendamento agendamento = novoAgendamento(novoCliente(), x);

        mockMvc.perform(delete("/chefe/me").cookie(tokenAntigo)).andExpect(status().isNoContent());

        mockMvc.perform(patch("/chefe/me").cookie(tokenAntigo).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Volta\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/pratos/" + prato.getId()).cookie(tokenAntigo)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nomePrato\":\"Novo\",\"descricao\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/pratos").file(new MockMultipartFile("dados", "", "application/json",
                                "{\"nomePrato\":\"Novo\",\"descricao\":\"x\"}".getBytes()))
                        .file(new MockMultipartFile("foto", "f.png", "image/png", PNG)).cookie(tokenAntigo))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/agendamentos/" + agendamento.getId() + "/confirmar").cookie(tokenAntigo))
                .andExpect(status().isForbidden());

        assertThat(agendamentoRepository.findById(agendamento.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.PENDENDE);
        assertThat(pratosRepository.findById(prato.getId()).orElseThrow().getNomePrato()).isEqualTo("Moqueca");
    }

    @Test
    void meDeClienteApagadoDa401EExpiraCookie() throws Exception {
        UserModel ana = novoCliente();
        Cookie tokenAntigo = cookieDe(ana);
        usuarioRepository.deleteById(ana.getId());

        mockMvc.perform(get("/auth/me").cookie(tokenAntigo))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                        .anyMatch(c -> c.startsWith(AuthCookies.ACCESS_TOKEN + "=") && c.contains("Max-Age=0")));
    }

    @Test
    void meDeChefInativoDa401EExpiraCookie() throws Exception {
        ChefeModel x = novoChefe();
        x.setAtivo(false);
        chefeRepository.save(x);

        mockMvc.perform(get("/auth/me").cookie(cookieDe(x)))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                        .anyMatch(c -> c.startsWith(AuthCookies.ACCESS_TOKEN + "=") && c.contains("Max-Age=0")));
    }

    @Test
    void meDeContaAtivaDevolveSessao() throws Exception {
        ChefeModel x = novoChefe();

        mockMvc.perform(get("/auth/me").cookie(cookieDe(x)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(x.getEmail()))
                .andExpect(jsonPath("$.tipo").value("CHEFE"));
    }

    UserModel novoCliente() {
        UserModel usuario = new UserModel();
        usuario.setNome("Cliente");
        usuario.setEmail("cliente-" + UUID.randomUUID() + "@x.com");
        usuario.setEndereco(new ArrayList<>());
        return usuarioRepository.save(usuario);
    }

    ChefeModel novoChefe() {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome("Chef");
        chefe.setEmail("chef-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("100"));
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        return chefeRepository.save(chefe);
    }

    Cookie cookieDe(UserModel usuario) {
        return cookie(new UsuarioAutenticado(usuario.getId(), usuario.getEmail(), TipoUsuario.CLIENTE));
    }

    Cookie cookieDe(ChefeModel chefe) {
        return cookie(new UsuarioAutenticado(chefe.getId(), chefe.getEmail(), TipoUsuario.CHEFE));
    }

    private Cookie cookie(UsuarioAutenticado usuario) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(usuario));
    }
}
