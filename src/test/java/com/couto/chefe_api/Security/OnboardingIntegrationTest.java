package com.couto.chefe_api.Security;

import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.EndercoRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private EndercoRepository enderecoRepository;

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

    private org.springframework.test.web.servlet.ResultActions registrar(String json) throws Exception {
        String registroToken = tokenService.gerarTokenRegistro(new GoogleUser(email, "Nome Google", null));
        return mockMvc.perform(post("/auth/register")
                .cookie(new Cookie(AuthCookies.REGISTRO_TOKEN, registroToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
