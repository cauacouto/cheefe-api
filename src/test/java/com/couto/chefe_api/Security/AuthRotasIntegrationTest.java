package com.couto.chefe_api.Security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rotas do cadastro e do logout pela cadeia de segurança inteira (o AuthControllerTest só cobre o controller). */
@SpringBootTest
@ActiveProfiles("test")
class AuthRotasIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void dadosDoCadastroSoComOTokenDeRegistro() throws Exception {
        mockMvc.perform(get("/auth/register")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/register").cookie(new Cookie(AuthCookies.REGISTRO_TOKEN, "forjado")))
                .andExpect(status().isUnauthorized());

        String token = tokenService.gerarTokenRegistro(new GoogleUser("ana@x.com", "Ana", "https://foto.test/ana.jpg"));
        mockMvc.perform(get("/auth/register").cookie(new Cookie(AuthCookies.REGISTRO_TOKEN, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@x.com"))
                .andExpect(jsonPath("$.nome").value("Ana"))
                .andExpect(jsonPath("$.foto").value("https://foto.test/ana.jpg"));
    }

    @Test
    void tokenDeAcessoNaoServeComoTokenDeRegistro() throws Exception {
        String acesso = tokenService.gerarToken(new UsuarioAutenticado(java.util.UUID.randomUUID(), "x@x.com",
                com.couto.chefe_api.enums.TipoUsuario.CLIENTE));

        mockMvc.perform(get("/auth/register").cookie(new Cookie(AuthCookies.REGISTRO_TOKEN, acesso)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutFuncionaSemSessaoEExpiraOsDoisCookies() throws Exception {
        List<String> cookies = mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);

        assertThat(cookies).anySatisfy(c -> assertThat(c).startsWith(AuthCookies.ACCESS_TOKEN + "=").contains("Max-Age=0"));
        assertThat(cookies).anySatisfy(c -> assertThat(c).startsWith(AuthCookies.REGISTRO_TOKEN + "=").contains("Max-Age=0"));
    }
}
