package com.couto.chefe_api.security;

import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloquear a conta derruba a sessão já aberta: o mesmo cookie deixa de valer na requisição seguinte.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContaBloqueadaIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ChefeRepository chefeRepository;

    private MockMvc mockMvc;
    private UUID clienteId;
    private UUID chefeId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void limpar() {
        if (clienteId != null) usuarioRepository.deleteById(clienteId);
        if (chefeId != null) chefeRepository.deleteById(chefeId);
    }

    @Test
    void sessaoAbertaCaiNaProximaRequisicao() throws Exception {
        UserModel cliente = new UserModel();
        cliente.setNome("Cliente");
        cliente.setEmail("cliente-" + UUID.randomUUID() + "@x.com");
        cliente.setEndereco(new ArrayList<>());
        cliente = usuarioRepository.save(cliente);
        clienteId = cliente.getId();
        Cookie clienteCookie = cookie(clienteId, cliente.getEmail(), TipoUsuario.CLIENTE);

        mockMvc.perform(get("/usuario/me").cookie(clienteCookie)).andExpect(status().isOk());
        cliente.setBloqueadoEm(LocalDateTime.now());
        usuarioRepository.save(cliente);
        mockMvc.perform(get("/usuario/me").cookie(clienteCookie)).andExpect(status().isUnauthorized());

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
        chefe = chefeRepository.save(chefe);
        chefeId = chefe.getId();
        Cookie chefeCookie = cookie(chefeId, chefe.getEmail(), TipoUsuario.CHEFE);

        mockMvc.perform(get("/chefe/me").cookie(chefeCookie)).andExpect(status().isOk());
        chefe.setBloqueadoEm(LocalDateTime.now());
        chefeRepository.save(chefe);
        mockMvc.perform(get("/chefe/me").cookie(chefeCookie)).andExpect(status().isUnauthorized());
    }

    private Cookie cookie(UUID id, String email, TipoUsuario tipo) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(new UsuarioAutenticado(id, email, tipo)));
    }
}
