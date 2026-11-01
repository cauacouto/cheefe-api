package com.couto.chefe_api.security;

import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import com.couto.chefe_api.domain.Notificacao;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.NotificacaoRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ChefeRepository chefeRepository;
    @Autowired
    private EnderecoRepository enderecoRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private com.couto.chefe_api.repository.PratosRepository pratosRepository;
    @Autowired
    private com.couto.chefe_api.MenuDeTeste menus;

    private MockMvc mockMvc;
    private final UUID ana = UUID.randomUUID();
    private final UUID bia = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void limparBanco() {
        agendamentoRepository.deleteAll();
        pratosRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
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

    @Test
    void criarAgendamentoNotificaOChef() throws Exception {
        UserModel cliente = novoCliente();
        ChefeModel chef = novoChefe(true);

        criarAgendamento(cliente, chef).andExpect(status().isCreated());

        mockMvc.perform(get("/notificacoes/nao-lidas/contagem").cookie(cookieDe(chef))).andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/notificacoes").cookie(cookieDe(chef)))
                .andExpect(jsonPath("$.content[0].evento").value("AGENDAMENTO_CRIADO"))
                .andExpect(jsonPath("$.content[0].mensagem").value(org.hamcrest.Matchers.startsWith("Cliente pediu um agendamento para ")));
    }

    @Test
    void editarNotificaOChefDepoisDaEdicao() throws Exception {
        UserModel cliente = novoCliente();
        ChefeModel x = novoChefe(true);
        criarAgendamento(cliente, x).andExpect(status().isCreated());
        Long id = agendamentoRepository.findAllByUsuario_Id(cliente.getId()).get(0).getId();

        mockMvc.perform(put("/agendamentos/" + id).cookie(cookieDe(cliente)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidadePessoas\":3}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/notificacoes").cookie(cookieDe(x)))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].evento").value("AGENDAMENTO_EDITADO"));
    }

    @Test
    void confirmarNotificaOCliente() throws Exception {
        UserModel cliente = novoCliente();
        ChefeModel chef = novoChefe(true);
        criarAgendamento(cliente, chef).andExpect(status().isCreated());
        Long id = agendamentoRepository.findAllByUsuario_Id(cliente.getId()).get(0).getId();

        mockMvc.perform(patch("/agendamentos/" + id + "/confirmar").cookie(cookieDe(chef))).andExpect(status().isOk());

        mockMvc.perform(get("/notificacoes").cookie(cookieDe(cliente)))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].evento").value("AGENDAMENTO_CONFIRMADO"))
                .andExpect(jsonPath("$.content[0].referenciaId").value(String.valueOf(id)));
    }

    @Test
    void agendamentoRecusadoNaoNotifica() throws Exception {
        UserModel cliente = novoCliente();
        ChefeModel incompleto = novoChefe(false);

        // fora da busca (perfil incompleto): o menu dele não pode ser reservado
        criarAgendamento(cliente, incompleto).andExpect(status().isConflict());

        assertThat(notificacaoRepository.countByDestinatarioIdAndLidaFalse(incompleto.getId())).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions criarAgendamento(UserModel cliente, ChefeModel chef) throws Exception {
        EnderecoModel endereco = new EnderecoModel();
        endereco.setRua("Rua A");
        endereco.setNumero("1");
        endereco.setBairro("Centro");
        endereco.setCidade("São Paulo");
        endereco.setEstado("SP");
        endereco.setCep("01001-000");
        endereco.setTipoDeResidencia(TipoDeResidencia.CASA);
        endereco.setUsuario(cliente);
        endereco = enderecoRepository.save(endereco);
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        LocalDateTime dia = LocalDateTime.now().plusDays(10);
        String json = "{\"menuId\":" + menus.ativo(chef).getId() + ","
                + "\"dataHoraInicial\":\"" + dia.withHour(18).withMinute(0).format(formato) + "\","
                + "\"enderecoId\":" + endereco.getId() + ",\"ocasiao\":\"FAMILIA\",\"quantidadePessoas\":2}";
        return mockMvc.perform(post("/agendamentos").cookie(cookie(cliente.getId(), cliente.getEmail(), TipoUsuario.CLIENTE))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private UserModel novoCliente() {
        UserModel usuario = new UserModel();
        usuario.setNome("Cliente");
        usuario.setEmail("cliente-" + UUID.randomUUID() + "@x.com");
        usuario.setEndereco(new ArrayList<>());
        return usuarioRepository.save(TelefoneDeTeste.verificado(usuario));
    }

    private ChefeModel novoChefe(boolean completo) {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome("Chef");
        chefe.setEmail("chef-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("100"));
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
        chefe.setAnosDeExperiencia(5);
        if (completo) {
            chefe.setSenioridade(Senioridade.HEAD_CHEF);
        }
        return chefeRepository.save(TelefoneDeTeste.verificado(chefe));
    }

    private Cookie cookieDe(UserModel usuario) {
        return cookie(usuario.getId(), usuario.getEmail(), TipoUsuario.CLIENTE);
    }

    private Cookie cookieDe(ChefeModel chefe) {
        return cookie(chefe.getId(), chefe.getEmail(), TipoUsuario.CHEFE);
    }

    private Cookie cookie(UUID id, String email, TipoUsuario tipo) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(new UsuarioAutenticado(id, email, tipo)));
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
