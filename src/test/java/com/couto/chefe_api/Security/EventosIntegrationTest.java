package com.couto.chefe_api.Security;

import com.couto.chefe_api.Services.SseRegistry;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.ConversaRepository;
import com.couto.chefe_api.repositorys.MensagemRepository;
import com.couto.chefe_api.repositorys.NotificacaoRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class EventosIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired ConversaRepository conversaRepository;
    @Autowired MensagemRepository mensagemRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @MockitoSpyBean SseRegistry sseRegistry;

    MockMvc mockMvc;
    UserModel ana;
    ChefeModel bia;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ana = new UserModel();
        ana.setNome("Ana");
        ana.setEmail("ana-" + UUID.randomUUID() + "@x.com");
        ana.setEndereco(new ArrayList<>());
        ana = usuarioRepository.save(ana);
        bia = new ChefeModel();
        bia.setNome("Bia");
        bia.setEmail("bia-" + UUID.randomUUID() + "@x.com");
        bia.setNacionalidade("Brasileira");
        bia.setIdiomas("pt");
        bia.setValorHora(new BigDecimal("100"));
        bia.setEspecialidade(Especialidades.BRASILEIRA);
        bia.setSenioridade(Senioridade.HEAD_CHEF);
        bia.setAnosDeExperiencia(5);
        bia = chefeRepository.save(bia);
    }

    @AfterEach
    void limparBanco() {
        mensagemRepository.deleteAll();
        conversaRepository.deleteAll();
        notificacaoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void eventosComCookieAbreStream() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/eventos").cookie(cookieAna()).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(resultado.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        verify(sseRegistry).abrir(ana.getId());
    }

    @Test
    void eventosSemCookieDa401() throws Exception {
        mockMvc.perform(get("/eventos").accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized());
    }

    @Test
    void falhaNoSseNaoDesfazAMensagem() throws Exception {
        doThrow(new IllegalStateException("cliente desconectado")).when(sseRegistry).enviar(any(), anyString(), any());
        String conversa = mockMvc.perform(post("/conversas").cookie(cookieAna()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chefeId\":\"" + bia.getId() + "\"}"))
                .andReturn().getResponse().getContentAsString();
        long conversaId = JSON.readTree(conversa).get("id").asLong();

        mockMvc.perform(post("/conversas/" + conversaId + "/mensagens").cookie(cookieAna())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                .andExpect(status().isCreated());

        verify(sseRegistry, atLeastOnce()).enviar(any(), anyString(), any());
        assertThat(mensagemRepository.findAll()).extracting("texto").containsExactly("oi");
        assertThat(notificacaoRepository.countByDestinatarioIdAndLidaFalse(bia.getId())).isEqualTo(1);
    }

    Cookie cookieAna() {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(ana.getId(), ana.getEmail(), TipoUsuario.CLIENTE)));
    }
}
