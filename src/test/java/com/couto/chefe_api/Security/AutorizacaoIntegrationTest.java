package com.couto.chefe_api.Security;

import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.ChefeRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ChefeRepository chefeRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
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
