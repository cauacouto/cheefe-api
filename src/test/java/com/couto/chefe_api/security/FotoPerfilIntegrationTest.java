package com.couto.chefe_api.security;

import com.couto.chefe_api.service.ImagemStorageService;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.enums.PastaImagem;
import com.couto.chefe_api.enums.TipoUsuario;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class FotoPerfilIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @MockitoBean
    private ImagemStorageService storage;

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
    void clienteTrocaFoto() throws Exception {
        UserModel ana = novoCliente();
        when(storage.enviar(any(), eq(PastaImagem.PERFIL))).thenReturn("https://cdn/perfil/ana.png");

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/usuario/me/foto")
                        .file(new MockMultipartFile("foto", "f.png", "image/png", new byte[]{1}))
                        .cookie(cookie(ana.getId(), ana.getEmail(), TipoUsuario.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("https://cdn/perfil/ana.png"));

        assertThat(usuarioRepository.findById(ana.getId()).orElseThrow().getImageUrl()).isEqualTo("https://cdn/perfil/ana.png");
    }

    @Test
    void clienteEmFotoDeChefDa403() throws Exception {
        UserModel ana = novoCliente();

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/chefe/me/foto")
                        .file(new MockMultipartFile("foto", "f.png", "image/png", new byte[]{1}))
                        .cookie(cookie(ana.getId(), ana.getEmail(), TipoUsuario.CLIENTE)))
                .andExpect(status().isForbidden());
    }

    private UserModel novoCliente() {
        UserModel usuario = new UserModel();
        usuario.setNome("Ana");
        usuario.setEmail("ana-" + UUID.randomUUID() + "@x.com");
        usuario.setEndereco(new ArrayList<>());
        return usuarioRepository.save(usuario);
    }

    private Cookie cookie(UUID id, String email, TipoUsuario tipo) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(new UsuarioAutenticado(id, email, tipo)));
    }
}
