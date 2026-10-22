package com.couto.chefe_api.security;

import com.couto.chefe_api.dto.ContagemDto;
import com.couto.chefe_api.service.NotificacaoService;
import com.couto.chefe_api.service.SseRegistry;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.ConversaRepository;
import com.couto.chefe_api.repository.MensagemRepository;
import com.couto.chefe_api.repository.NotificacaoRepository;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    @Autowired NotificacaoService notificacaoService;
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
    void eventoChegaSerializadoNoStreamEODispatchAsyncPassa() throws Exception {
        AtomicReference<SseEmitter> aberto = new AtomicReference<>();
        doAnswer(inv -> {
            SseEmitter emitter = (SseEmitter) inv.callRealMethod();
            aberto.set(emitter);
            return emitter;
        }).when(sseRegistry).abrir(any());
        MvcResult stream = mockMvc.perform(get("/eventos").cookie(cookieAna()).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
        long conversaId = JSON.readTree(mockMvc.perform(post("/conversas").cookie(cookieAna())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"chefeId\":\"" + bia.getId() + "\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/conversas/" + conversaId + "/mensagens").cookie(cookieBia())
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"chego às 19h\"}"))
                .andExpect(status().isCreated());
        aberto.get().complete();

        mockMvc.perform(asyncDispatch(stream)).andExpect(status().isOk());
        String recebido = stream.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8); // SSE é sempre UTF-8
        assertThat(recebido).contains("event:mensagem").contains("\"texto\":\"chego às 19h\"").contains("\"minha\":false")
                .containsPattern("\"enviadaEm\":\"\\d{2}-\\d{2}-\\d{4} \\d{2}:\\d{2}\"");
    }

    @Test
    void lerAtualizaOContadorDoSinoNasOutrasAbas() throws Exception {
        long conversaId = JSON.readTree(mockMvc.perform(post("/conversas").cookie(cookieAna())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"chefeId\":\"" + bia.getId() + "\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/conversas/" + conversaId + "/mensagens").cookie(cookieAna())
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}")).andExpect(status().isCreated());
        verify(sseRegistry).enviar(bia.getId(), "notificacao", new ContagemDto(1));

        mockMvc.perform(patch("/conversas/" + conversaId + "/lidas").cookie(cookieBia())).andExpect(status().isNoContent());
        verify(sseRegistry).enviar(bia.getId(), "notificacao", new ContagemDto(0));

        // nada mudou: nada a avisar
        mockMvc.perform(patch("/notificacoes/lidas").cookie(cookieBia())).andExpect(status().isNoContent());
        verify(sseRegistry, times(1)).enviar(bia.getId(), "notificacao", new ContagemDto(0));
    }

    @Test
    void marcarUmaOuTodasComoLidasAvisaOContador() throws Exception {
        notificacaoService.notificar(bia.getId(), TipoUsuario.CHEFE, EventoNotificacao.AGENDAMENTO_CRIADO, "a", "1");
        notificacaoService.notificar(bia.getId(), TipoUsuario.CHEFE, EventoNotificacao.AGENDAMENTO_CRIADO, "b", "2");
        long primeira = notificacaoRepository.findAll().get(0).getId();

        mockMvc.perform(patch("/notificacoes/" + primeira + "/lida").cookie(cookieBia())).andExpect(status().isNoContent());
        verify(sseRegistry, times(2)).enviar(bia.getId(), "notificacao", new ContagemDto(1));
        mockMvc.perform(patch("/notificacoes/" + primeira + "/lida").cookie(cookieBia())).andExpect(status().isNoContent());
        verify(sseRegistry, times(2)).enviar(bia.getId(), "notificacao", new ContagemDto(1));

        mockMvc.perform(patch("/notificacoes/lidas").cookie(cookieBia())).andExpect(status().isNoContent());
        verify(sseRegistry).enviar(bia.getId(), "notificacao", new ContagemDto(0));
    }

    @Test
    void contaApagadaAtualizaOSinoDeQuemTinhaNotificacaoDela() throws Exception {
        long conversaId = JSON.readTree(mockMvc.perform(post("/conversas").cookie(cookieAna())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"chefeId\":\"" + bia.getId() + "\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/conversas/" + conversaId + "/mensagens").cookie(cookieAna())
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}")).andExpect(status().isCreated());
        verify(sseRegistry).enviar(bia.getId(), "notificacao", new ContagemDto(1));

        mockMvc.perform(delete("/usuario/me").cookie(cookieAna())).andExpect(status().isNoContent());

        verify(sseRegistry).enviar(bia.getId(), "notificacao", new ContagemDto(0));
        verify(sseRegistry, never()).enviar(eq(ana.getId()), eq("notificacao"), any());
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

    Cookie cookieBia() {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(bia.getId(), bia.getEmail(), TipoUsuario.CHEFE)));
    }

    Cookie cookieAna() {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(ana.getId(), ana.getEmail(), TipoUsuario.CLIENTE)));
    }
}
