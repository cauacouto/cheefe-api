package com.couto.chefe_api.security;

import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.ChefeRepository;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pausa ({@code /inativar}) esconde o chef da busca sem bloquear a conta; {@code /ativar} desfaz.
 */
@SpringBootTest
@ActiveProfiles("test")
class PausaChefIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private ChefeRepository chefeRepository;
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
        chefeRepository.deleteAll();
    }

    @Test
    void chefPausadoSomeDaBuscaContinuaOperandoEVolta() throws Exception {
        ChefeModel chef = novoChefe();
        Cookie cookie = cookieDe(chef);

        mockMvc.perform(patch("/chefe/me/inativar").cookie(cookie)).andExpect(status().isNoContent());
        mockMvc.perform(get("/chefe").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(patch("/chefe/me").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Em férias\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/chefe/me/ativar").cookie(cookie)).andExpect(status().isNoContent());
        mockMvc.perform(get("/chefe").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        assertThat(chefeRepository.findById(chef.getId()).orElseThrow().isDisponivel()).isTrue();
    }

    @Test
    void clienteNaoAtivaChef() throws Exception {
        UserModel cliente = new UserModel();
        cliente.setNome("Cliente");
        cliente.setEmail("cliente-" + UUID.randomUUID() + "@x.com");
        cliente.setEndereco(new ArrayList<>());
        cliente = usuarioRepository.save(TelefoneDeTeste.verificado(cliente));

        mockMvc.perform(patch("/chefe/me/ativar").cookie(cookie(new UsuarioAutenticado(cliente.getId(), cliente.getEmail(), TipoUsuario.CLIENTE))))
                .andExpect(status().isForbidden());
    }

    private ChefeModel novoChefe() {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome("Chef");
        chefe.setEmail("chef-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("100"));
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        return chefeRepository.save(TelefoneDeTeste.verificado(chefe));
    }

    private Cookie cookieDe(ChefeModel chefe) {
        return cookie(new UsuarioAutenticado(chefe.getId(), chefe.getEmail(), TipoUsuario.CHEFE));
    }

    private Cookie cookie(UsuarioAutenticado usuario) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(usuario));
    }
}
