package com.couto.chefe_api.Security;

import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.ConversaRepository;
import com.couto.chefe_api.repositorys.DenunciaMensagemRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ConversaIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired ConversaRepository conversaRepository;
    @Autowired MensagemRepository mensagemRepository;
    @Autowired DenunciaMensagemRepository denunciaMensagemRepository;
    @Autowired NotificacaoRepository notificacaoRepository;

    MockMvc mockMvc;
    UserModel ana;
    ChefeModel bia;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ana = novoCliente("Ana");
        bia = novoChefe("Bia");
    }

    @AfterEach
    void limparBanco() {
        denunciaMensagemRepository.deleteAll();
        mensagemRepository.deleteAll();
        conversaRepository.deleteAll();
        notificacaoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void abrirConversaEhIdempotenteEChefNaoAbre() throws Exception {
        long id = abrir(ana, bia).andExpect(status().isCreated()).andReturnId();
        long deNovo = abrir(ana, bia).andExpect(status().isOk()).andReturnId();

        assertThat(deNovo).isEqualTo(id);
        mockMvc.perform(post("/conversas").cookie(cookie(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chefeId\":\"" + bia.getId() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void trocaDeMensagensComNaoLidasPreviaEVisto() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        enviar(ana, conversa, "  Oi, você faz moqueca?  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.texto").value("Oi, você faz moqueca?"))
                .andExpect(jsonPath("$.minha").value(true));

        mockMvc.perform(get("/conversas").cookie(cookie(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].naoLidas").value(1))
                .andExpect(jsonPath("$[0].ultimaMensagem.texto").value("Oi, você faz moqueca?"))
                .andExpect(jsonPath("$[0].outroParticipante.nome").value("Ana"));

        mockMvc.perform(patch("/conversas/" + conversa + "/lidas").cookie(cookie(bia))).andExpect(status().isNoContent());

        mockMvc.perform(get("/conversas").cookie(cookie(bia))).andExpect(jsonPath("$[0].naoLidas").value(0));
        mockMvc.perform(get("/conversas/" + conversa + "/mensagens").cookie(cookie(ana)))
                .andExpect(jsonPath("$[0].lida").value(true))
                .andExpect(jsonPath("$[0].lidaEm").isNotEmpty());
    }

    @Test
    void dezMensagensUmaNotificacaoEDepoisDeLerNotificaDeNovo() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        for (int i = 0; i < 10; i++) {
            enviar(ana, conversa, "mensagem " + i).andExpect(status().isCreated());
        }
        mockMvc.perform(get("/notificacoes/nao-lidas/contagem").cookie(cookie(bia))).andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/notificacoes").cookie(cookie(bia)))
                .andExpect(jsonPath("$.content[0].evento").value("MENSAGEM_RECEBIDA"))
                .andExpect(jsonPath("$.content[0].mensagem").value("Nova mensagem de Ana"))
                .andExpect(jsonPath("$.content[0].referenciaId").value(String.valueOf(conversa)));

        mockMvc.perform(patch("/conversas/" + conversa + "/lidas").cookie(cookie(bia))).andExpect(status().isNoContent());
        mockMvc.perform(get("/notificacoes/nao-lidas/contagem").cookie(cookie(bia))).andExpect(jsonPath("$.total").value(0));

        enviar(ana, conversa, "mais uma").andExpect(status().isCreated());
        mockMvc.perform(get("/notificacoes/nao-lidas/contagem").cookie(cookie(bia))).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void paginacaoComAntesDe() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        for (int i = 1; i <= 5; i++) {
            enviar(ana, conversa, "m" + i);
        }
        String pagina1 = mockMvc.perform(get("/conversas/" + conversa + "/mensagens").param("size", "2").cookie(cookie(ana)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].texto").value("m5"))
                .andReturn().getResponse().getContentAsString();
        long ultimoId = JSON.readTree(pagina1).get(1).get("id").asLong();

        mockMvc.perform(get("/conversas/" + conversa + "/mensagens").param("size", "2").param("antesDe", String.valueOf(ultimoId))
                        .cookie(cookie(ana)))
                .andExpect(jsonPath("$[0].texto").value("m3"))
                .andExpect(jsonPath("$[1].texto").value("m2"));
    }

    @Test
    void terceiroNaoVeNemMexeNaConversa() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        UserModel intruso = novoCliente("Intruso");

        mockMvc.perform(get("/conversas/" + conversa + "/mensagens").cookie(cookie(intruso))).andExpect(status().isNotFound());
        enviar(intruso, conversa, "oi").andExpect(status().isNotFound());
        mockMvc.perform(patch("/conversas/" + conversa + "/lidas").cookie(cookie(intruso))).andExpect(status().isNotFound());
        mockMvc.perform(get("/conversas").cookie(cookie(intruso))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void textoInvalidoDa400() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();

        enviar(ana, conversa, "   ").andExpect(status().isBadRequest());
        enviar(ana, conversa, "x".repeat(2001)).andExpect(status().isBadRequest());
    }

    @Test
    void chefApagadoBloqueiaEnvioNosDoisSentidos() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        bia.setAtivo(false);
        chefeRepository.save(bia);

        enviar(ana, conversa, "oi").andExpect(status().isForbidden());
        mockMvc.perform(get("/conversas/" + conversa + "/mensagens").cookie(cookie(ana))).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------

    record Resposta(ResultActions acoes) {
        Resposta andExpect(org.springframework.test.web.servlet.ResultMatcher m) throws Exception {
            acoes.andExpect(m);
            return this;
        }

        long andReturnId() throws Exception {
            return JSON.readTree(acoes.andReturn().getResponse().getContentAsString()).get("id").asLong();
        }
    }

    Resposta abrir(UserModel cliente, ChefeModel chefe) throws Exception {
        return new Resposta(mockMvc.perform(post("/conversas").cookie(cookie(cliente)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"chefeId\":\"" + chefe.getId() + "\"}")));
    }

    ResultActions enviar(UserModel cliente, long conversa, String texto) throws Exception {
        return mockMvc.perform(post("/conversas/" + conversa + "/mensagens").cookie(cookie(cliente))
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(java.util.Map.of("texto", texto))));
    }

    UserModel novoCliente(String nome) {
        UserModel usuario = new UserModel();
        usuario.setNome(nome);
        usuario.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        usuario.setEndereco(new ArrayList<>());
        return usuarioRepository.save(usuario);
    }

    ChefeModel novoChefe(String nome) {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome(nome);
        chefe.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("100"));
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        return chefeRepository.save(chefe);
    }

    Cookie cookie(UserModel cliente) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(cliente.getId(), cliente.getEmail(), TipoUsuario.CLIENTE)));
    }

    Cookie cookie(ChefeModel chefe) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(chefe.getId(), chefe.getEmail(), TipoUsuario.CHEFE)));
    }
}
