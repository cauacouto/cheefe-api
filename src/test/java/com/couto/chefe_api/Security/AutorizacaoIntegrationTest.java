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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
