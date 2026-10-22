package com.couto.chefe_api.Security;

import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.enums.TipoUsuario;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Papel de moderação: vem da configuração (moderacao.emails), não do banco; a área /moderacao
 * parece não existir para quem não é moderador.
 */
@SpringBootTest
@ActiveProfiles("test")
class ModeracaoIntegrationTest {

    static final String EMAIL_MODERACAO = "moderacao@chefhome.test";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private ModeracaoRegistry moderacaoRegistry;
    @Autowired
    private UsuarioRepository usuarioRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void limparBanco() {
        usuarioRepository.deleteAll();
    }

    @Test
    void moderadorVeAuthMe() throws Exception {
        mockMvc.perform(get("/auth/me").cookie(cookieModerador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("MODERADOR"));
    }

    @Test
    void clienteEmRotaDeModeracaoDa404() throws Exception {
        mockMvc.perform(get("/moderacao/denuncias").cookie(cookieCliente()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void moderadorEmRotaDeClienteDa403() throws Exception {
        mockMvc.perform(get("/usuario/me").cookie(cookieModerador()))
                .andExpect(status().isForbidden());
    }

    Cookie cookieModerador() {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(moderacaoRegistry.idDe(EMAIL_MODERACAO), EMAIL_MODERACAO, TipoUsuario.MODERADOR)));
    }

    Cookie cookieCliente() {
        UserModel cliente = new UserModel();
        cliente.setNome("Cliente");
        cliente.setEmail("cliente-" + UUID.randomUUID() + "@x.com");
        cliente.setEndereco(new ArrayList<>());
        cliente = usuarioRepository.save(cliente);
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(cliente.getId(), cliente.getEmail(), TipoUsuario.CLIENTE)));
    }
}
