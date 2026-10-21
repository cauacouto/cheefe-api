package com.couto.chefe_api.Security;

import com.couto.chefe_api.domin.Notificacao;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.NotificacaoRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class NotificacaoIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private NotificacaoRepository notificacaoRepository;

    private MockMvc mockMvc;
    private final UUID ana = UUID.randomUUID();
    private final UUID bia = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void limparBanco() {
        notificacaoRepository.deleteAll();
    }

    @Test
    void listaSoAsProprias() throws Exception {
        notificacao(ana, "primeira");
        notificacao(ana, "segunda");
        notificacao(bia, "da Bia");

        mockMvc.perform(get("/notificacoes").cookie(cookieDe(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].mensagem").value("segunda"));
    }

    @Test
    void filtraNaoLidas() throws Exception {
        notificacao(ana, "nao lida");
        Notificacao lida = notificacao(ana, "lida");
        lida.setLida(true);
        notificacaoRepository.save(lida);

        mockMvc.perform(get("/notificacoes").param("somenteNaoLidas", "true").cookie(cookieDe(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].mensagem").value("nao lida"));
    }

    @Test
    void contagemDeNaoLidas() throws Exception {
        notificacao(ana, "a");
        notificacao(ana, "b");

        mockMvc.perform(get("/notificacoes/nao-lidas/contagem").cookie(cookieDe(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void marcarLidaDuasVezesBaixaUmaVez() throws Exception {
        Notificacao primeira = notificacao(ana, "a");
        notificacao(ana, "b");

        mockMvc.perform(patch("/notificacoes/" + primeira.getId() + "/lida").cookie(cookieDe(ana))).andExpect(status().isNoContent());
        mockMvc.perform(patch("/notificacoes/" + primeira.getId() + "/lida").cookie(cookieDe(ana))).andExpect(status().isNoContent());

        mockMvc.perform(get("/notificacoes/nao-lidas/contagem").cookie(cookieDe(ana)))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void marcarLidaDeOutraPessoaDa404() throws Exception {
        Notificacao daBia = notificacao(bia, "da Bia");

        mockMvc.perform(patch("/notificacoes/" + daBia.getId() + "/lida").cookie(cookieDe(ana)))
                .andExpect(status().isNotFound());

        assertThat(notificacaoRepository.findById(daBia.getId()).orElseThrow().isLida()).isFalse();
    }

    @Test
    void marcarTodasSoAfetaQuemChamou() throws Exception {
        notificacao(ana, "a");
        notificacao(ana, "b");
        notificacao(bia, "c");

        mockMvc.perform(patch("/notificacoes/lidas").cookie(cookieDe(ana))).andExpect(status().isNoContent());

        mockMvc.perform(get("/notificacoes/nao-lidas/contagem").cookie(cookieDe(ana))).andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/notificacoes/nao-lidas/contagem").cookie(cookieDe(bia))).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void semCookieDa401() throws Exception {
        mockMvc.perform(get("/notificacoes")).andExpect(status().isUnauthorized());
    }

    private Notificacao notificacao(UUID destinatario, String mensagem) {
        Notificacao notificacao = new Notificacao();
        notificacao.setDestinatarioId(destinatario);
        notificacao.setDestinatarioTipo(TipoUsuario.CLIENTE);
        notificacao.setEvento(EventoNotificacao.AGENDAMENTO_CONFIRMADO);
        notificacao.setMensagem(mensagem);
        notificacao.setReferenciaId("1");
        return notificacaoRepository.save(notificacao);
    }

    private Cookie cookieDe(UUID id) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(new UsuarioAutenticado(id, id + "@x.com", TipoUsuario.CLIENTE)));
    }
}
