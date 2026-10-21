package com.couto.chefe_api.Security;

import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.enums.TipoUsuario;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class OAuth2FlowIntegrationTest {

    private static final String ERRO = "http://localhost:5173/login?erro=google";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void inicioDoFluxoRedirecionaParaGoogleComPkce() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().isFound())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertTrue(location.startsWith("https://accounts.google.com/o/oauth2/v2/auth"));
        assertTrue(location.contains("state="));
        assertTrue(location.contains("code_challenge="));
        assertTrue(location.contains("code_challenge_method=S256"));
        String cookie = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith("oauth2_auth_request="))
                .findFirst()
                .orElseThrow();
        assertTrue(cookie.length() < 4096, "navegadores descartam cookies acima de 4KB");
        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void callbackSemCookieRedirecionaParaErro() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/google").param("code", "abc").param("state", "xyz"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(ERRO));
    }

    @Test
    void usuarioCancelouRedirecionaParaErro() throws Exception {
        FluxoIniciado fluxo = iniciarFluxo();

        mockMvc.perform(get("/login/oauth2/code/google")
                        .param("error", "access_denied")
                        .param("state", fluxo.state())
                        .cookie(fluxo.cookie()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(ERRO));
    }

    @Test
    void stateDivergenteComCookieRedirecionaParaErro() throws Exception {
        FluxoIniciado fluxo = iniciarFluxo();

        mockMvc.perform(get("/login/oauth2/code/google")
                        .param("code", "abc")
                        .param("state", "outro-state")
                        .cookie(fluxo.cookie()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(ERRO));
    }

    private FluxoIniciado iniciarFluxo() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google")).andReturn();
        String location = result.getResponse().getHeader("Location");
        String state = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
        String header = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith(AuthCookies.OAUTH2_REQUEST + "="))
                .findFirst()
                .orElseThrow();
        String valor = header.substring(header.indexOf('=') + 1, header.indexOf(';'));
        return new FluxoIniciado(URLDecoder.decode(state, StandardCharsets.UTF_8), new Cookie(AuthCookies.OAUTH2_REQUEST, valor));
    }

    private record FluxoIniciado(String state, Cookie cookie) {
    }

    @Test
    void rotaProtegidaSemCookieDa401() throws Exception {
        mockMvc.perform(get("/usuario/enderecos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotaProtegidaComTokenAdulteradoDa401() throws Exception {
        mockMvc.perform(get("/usuario/enderecos").cookie(new Cookie(AuthCookies.ACCESS_TOKEN, "lixo")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meComTokenValidoDevolveSessao() throws Exception {
        UserModel ana = new UserModel();
        ana.setNome("Ana");
        ana.setEmail("ana-" + UUID.randomUUID() + "@gmail.com");
        ana.setEndereco(new ArrayList<>());
        ana = usuarioRepository.save(ana);
        try {
            String token = tokenService.gerarToken(new UsuarioAutenticado(ana.getId(), ana.getEmail(), TipoUsuario.CLIENTE));

            mockMvc.perform(get("/auth/me").cookie(new Cookie(AuthCookies.ACCESS_TOKEN, token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(ana.getEmail()))
                    .andExpect(jsonPath("$.tipo").value("CLIENTE"));
        } finally {
            usuarioRepository.deleteById(ana.getId());
        }
    }

    @Test
    void meComTokenDeContaInexistenteDa401EExpiraCookie() throws Exception {
        String token = tokenService.gerarToken(new UsuarioAutenticado(UUID.randomUUID(), "fantasma@gmail.com", TipoUsuario.CLIENTE));

        mockMvc.perform(get("/auth/me").cookie(new Cookie(AuthCookies.ACCESS_TOKEN, token)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie",
                        allOf(startsWith(AuthCookies.ACCESS_TOKEN + "="), containsString("Max-Age=0"))));
    }

    @Test
    void registerAceitaJsonESemCookieDeRegistroDa401() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoUsuario\":\"CLIENTE\",\"nome\":\"Ana\",\"endereco\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsPermitePatchDoFront() throws Exception {
        mockMvc.perform(options("/chefe/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")));
    }

    @Test
    void faviconSemCookieNaoDa401() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound());
    }

    @Test
    void meSemCookieDa401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
