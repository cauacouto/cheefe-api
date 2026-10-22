package com.couto.chefe_api.Security;

import com.couto.chefe_api.Excepitons.ContaInativaException;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.Denuncia;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.MotivoDenuncia;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.repositorys.AcaoModeracaoRepository;
import com.couto.chefe_api.repositorys.AgendamentoRepository;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.DenunciaRepository;
import com.couto.chefe_api.repositorys.NotificacaoRepository;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.util.List;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @Autowired
    private ChefeRepository chefeRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private DenunciaRepository denunciaRepository;
    @Autowired
    private NotificacaoRepository notificacaoRepository;
    @Autowired
    private LoginService loginService;
    @Autowired
    private AcaoModeracaoRepository acaoModeracaoRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void limparBanco() {
        acaoModeracaoRepository.deleteAll();
        denunciaRepository.deleteAll();
        agendamentoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
        notificacaoRepository.deleteAll();
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

    @Test
    void filaListaAbertas() throws Exception {
        ChefeModel chefe = novoChefe();
        denunciar(chefe, StatusDenuncia.ABERTA);
        denunciar(chefe, StatusDenuncia.ARQUIVADA);

        mockMvc.perform(get("/moderacao/denuncias").cookie(cookieModerador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].chefe.nome").value("Bia"))
                .andExpect(jsonPath("$.content[0].status").value("ABERTA"));
    }

    @Test
    void arquivarFazChefVoltar() throws Exception {
        ChefeModel chefe = novoChefe();
        Denuncia primeira = denunciar(chefe, StatusDenuncia.ABERTA);
        denunciar(chefe, StatusDenuncia.ABERTA);
        denunciar(chefe, StatusDenuncia.ABERTA);
        Cookie moderador = cookieModerador();
        Cookie cliente = cookieCliente();

        mockMvc.perform(get("/chefe").cookie(cliente)).andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(patch("/moderacao/denuncias/" + primeira.getId() + "/arquivar").cookie(moderador))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/chefe").cookie(cliente)).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void suspenderEReativarPelaApi() throws Exception {
        ChefeModel chefe = novoChefe();

        mockMvc.perform(patch("/moderacao/chefes/" + chefe.getId() + "/suspender").cookie(cookieModerador()))
                .andExpect(status().isNoContent());
        assertThat(chefeRepository.findById(chefe.getId()).orElseThrow().isSuspenso()).isTrue();
        mockMvc.perform(patch("/moderacao/chefes/" + chefe.getId() + "/reativar").cookie(cookieModerador()))
                .andExpect(status().isNoContent());
        assertThat(chefeRepository.findById(chefe.getId()).orElseThrow().isSuspenso()).isFalse();
    }

    @Test
    void banidoNaoLoga() throws Exception {
        ChefeModel chefe = novoChefe();

        mockMvc.perform(patch("/moderacao/chefes/" + chefe.getId() + "/banir").cookie(cookieModerador()))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> loginService.loginGoogle(new GoogleUser(chefe.getEmail(), "Bia", null)))
                .isInstanceOf(ContaInativaException.class);
    }

    @Test
    void clienteNaoUsaModeracao() throws Exception {
        ChefeModel chefe = novoChefe();

        mockMvc.perform(patch("/moderacao/chefes/" + chefe.getId() + "/banir").cookie(cookieCliente()))
                .andExpect(status().isNotFound());
        assertThat(chefeRepository.findById(chefe.getId()).orElseThrow().getAtivo()).isTrue();
    }

    @Test
    void moderadorNaoDenunciaNemAgenda() throws Exception {
        mockMvc.perform(post("/denuncias").cookie(cookieModerador()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/agendamentos").cookie(cookieModerador()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    ChefeModel novoChefe() {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome("Bia");
        chefe.setEmail("chef-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("100"));
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        return chefeRepository.save(chefe);
    }

    Denuncia denunciar(ChefeModel chefe, StatusDenuncia status) {
        UserModel cliente = new UserModel();
        cliente.setNome("Cliente");
        cliente.setEmail("cliente-" + UUID.randomUUID() + "@x.com");
        cliente = usuarioRepository.save(cliente);
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setChefes(new ArrayList<>(List.of(chefe)));
        agendamento = agendamentoRepository.save(agendamento);
        Denuncia denuncia = new Denuncia();
        denuncia.setChefe(chefe);
        denuncia.setCliente(cliente);
        denuncia.setAgendamento(agendamento);
        denuncia.setMotivo(MotivoDenuncia.COMPORTAMENTO);
        denuncia.setDescricao("grosseiro");
        denuncia.setStatus(status);
        return denunciaRepository.save(denuncia);
    }

    @Test
    void moderadorRemovidoDaConfiguracaoPerdeOAcessoNaHora() throws Exception {
        ChefeModel chefe = novoChefe();
        String exModerador = "ex-moderador@x.com";
        Cookie tokenAntigo = new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(moderacaoRegistry.idDe(exModerador), exModerador, TipoUsuario.MODERADOR)));

        mockMvc.perform(get("/moderacao/denuncias").cookie(tokenAntigo)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/moderacao/chefes/" + chefe.getId() + "/banir").cookie(tokenAntigo))
                .andExpect(status().isUnauthorized());

        assertThat(chefeRepository.findById(chefe.getId()).orElseThrow().getAtivo()).isTrue();
    }

    @Test
    void areaDeModeracaoResponde404IgualAUmaRotaInexistente() throws Exception {
        Cookie cliente = cookieCliente();

        var real = mockMvc.perform(get("/rota-que-nao-existe").cookie(cliente)).andReturn().getResponse();
        var escondida = mockMvc.perform(get("/moderacao/denuncias").cookie(cliente)).andReturn().getResponse();

        assertThat(escondida.getStatus()).isEqualTo(real.getStatus()).isEqualTo(404);
        assertThat(escondida.getContentType()).isEqualTo(real.getContentType());
        assertThat(escondida.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(real.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                        .replace("rota-que-nao-existe", "moderacao/denuncias"));
    }

    @Test
    void historicoDeAcoesMostraQuemFezOQue() throws Exception {
        ChefeModel chefe = novoChefe();

        mockMvc.perform(patch("/moderacao/chefes/" + chefe.getId() + "/suspender").cookie(cookieModerador()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/moderacao/acoes").cookie(cookieModerador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].acao").value("SUSPENDER_CHEF"))
                .andExpect(jsonPath("$.content[0].moderadorEmail").value(EMAIL_MODERACAO))
                .andExpect(jsonPath("$.content[0].chefeId").value(chefe.getId().toString()));
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
