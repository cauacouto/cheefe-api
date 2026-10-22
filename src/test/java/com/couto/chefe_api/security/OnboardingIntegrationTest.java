package com.couto.chefe_api.security;

import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Onboarding de quem é novo: nome e tipo; cliente também informa endereço.
 */
@SpringBootTest
@ActiveProfiles("test")
class OnboardingIntegrationTest {

    private static final String ENDERECO_CASA = """
            {"rua":"Rua A","numero":"1","cep":"01001-000","cidade":"São Paulo","bairro":"Centro",
             "estado":"SP","tipoDeResidencia":"CASA"}""";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ChefeRepository chefeRepository;
    @Autowired
    private EnderecoRepository enderecoRepository;

    private MockMvc mockMvc;
    private String email;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        email = "novo-" + UUID.randomUUID() + "@gmail.com";
    }

    @AfterEach
    void limparBanco() {
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void chefSoComNomeEhCriado() throws Exception {
        registrar("{\"tipoUsuario\":\"CHEFE\",\"nome\":\"Bia\"}").andExpect(status().isCreated());

        ChefeModel chefe = chefeRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(chefe.isPerfilCompleto()).isFalse();
    }

    @Test
    void chefComValoresInvalidosNoRegistroDa400() throws Exception {
        registrar("{\"tipoUsuario\":\"CHEFE\",\"nome\":\"Bia\",\"valorHora\":-100,\"anosDeExperiencia\":-4}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].campo").value(containsInAnyOrder("valorHora", "anosDeExperiencia")));

        assertThat(chefeRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void clienteSemEnderecoDa400() throws Exception {
        registrar("{\"tipoUsuario\":\"CLIENTE\",\"nome\":\"Ana\"}").andExpect(status().isBadRequest());
        assertThat(usuarioRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void clienteComEnderecoVazioDa400() throws Exception {
        registrar("{\"tipoUsuario\":\"CLIENTE\",\"nome\":\"Ana\",\"endereco\":[]}").andExpect(status().isBadRequest());
        assertThat(usuarioRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void apartamentoSemComplementoDa400SemGravarNada() throws Exception {
        String apartamento = ENDERECO_CASA.replace("\"CASA\"", "\"APARTAMENTO\"");

        registrar("{\"tipoUsuario\":\"CLIENTE\",\"nome\":\"Ana\",\"endereco\":[" + apartamento + "]}")
                .andExpect(status().isBadRequest());

        assertThat(usuarioRepository.findByEmailIgnoreCase(email)).isEmpty();
        assertThat(enderecoRepository.count()).isZero();
    }

    @Test
    void clienteComEnderecoValidoEhCriado() throws Exception {
        registrar("{\"tipoUsuario\":\"CLIENTE\",\"nome\":\"Ana\",\"endereco\":[" + ENDERECO_CASA + "]}")
                .andExpect(status().isCreated());

        assertThat(usuarioRepository.findByEmailIgnoreCase(email)).isPresent();
        assertThat(enderecoRepository.count()).isEqualTo(1);
    }

    @Test
    void chefRecemCriadoVeCamposPendentes() throws Exception {
        ChefeModel chefe = chefeSoComNome();

        mockMvc.perform(get("/chefe/me").cookie(cookieDe(chefe)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfilCompleto").value(false))
                .andExpect(jsonPath("$.camposPendentes").value(contains(
                        "nacionalidade", "especialidade", "senioridade", "anosDeExperiencia", "idiomas", "valorHora")));
    }

    @Test
    void completarPerfilViaPatchTornaVisivel() throws Exception {
        ChefeModel chefe = chefeSoComNome();
        Cookie cookie = cookieDe(chefe);

        mockMvc.perform(patch("/chefe/me").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("""
                        {"nacionalidade":"Brasileira","especialidade":"BRASILEIRA","senioridade":"HEAD_CHEF",
                         "anosDeExperiencia":5,"idiomas":"pt","valorHora":150}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/chefe/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfilCompleto").value(true))
                .andExpect(jsonPath("$.camposPendentes").isEmpty());
        mockMvc.perform(get("/chefe").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + chefe.getId() + "')]").exists());
    }

    @Test
    void clienteEmChefeMeDa403() throws Exception {
        UserModel cliente = new UserModel();
        cliente.setNome("Ana");
        cliente.setEmail(email);
        cliente.setEndereco(new ArrayList<>());
        cliente = usuarioRepository.save(cliente);

        mockMvc.perform(get("/chefe/me").cookie(new Cookie(AuthCookies.ACCESS_TOKEN,
                        tokenService.gerarToken(new UsuarioAutenticado(cliente.getId(), cliente.getEmail(), TipoUsuario.CLIENTE)))))
                .andExpect(status().isForbidden());
    }

    private ChefeModel chefeSoComNome() {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome("Bia");
        chefe.setEmail(email);
        return chefeRepository.save(chefe);
    }

    private Cookie cookieDe(ChefeModel chefe) {
        return new Cookie(AuthCookies.ACCESS_TOKEN,
                tokenService.gerarToken(new UsuarioAutenticado(chefe.getId(), chefe.getEmail(), TipoUsuario.CHEFE)));
    }

    private org.springframework.test.web.servlet.ResultActions registrar(String json) throws Exception {
        String registroToken = tokenService.gerarTokenRegistro(new GoogleUser(email, "Nome Google", null));
        return mockMvc.perform(post("/auth/register")
                .cookie(new Cookie(AuthCookies.REGISTRO_TOKEN, registroToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
