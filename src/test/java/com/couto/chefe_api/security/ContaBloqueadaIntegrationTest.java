package com.couto.chefe_api.security;

import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.domain.BloqueioConta;
import com.couto.chefe_api.enums.CategoriaConteudo;
import com.couto.chefe_api.enums.StatusBloqueio;
import com.couto.chefe_api.repository.AcaoModeracaoRepository;
import com.couto.chefe_api.repository.BloqueioContaRepository;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @Autowired
    private LoginService loginService;
    @Autowired
    private ModeracaoRegistry moderacaoRegistry;
    @Autowired
    private BloqueioContaRepository bloqueioRepository;
    @Autowired
    private AcaoModeracaoRepository acaoRepository;

    private MockMvc mockMvc;
    private UUID clienteId;
    private UUID chefeId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void limpar() {
        bloqueioRepository.deleteAll();
        acaoRepository.deleteAll();
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
        chefe.setBloqueadoEm(LocalDateTime.now());
        chefe.setBloqueioMotivo("AMEACA");
        chefe = chefeRepository.save(chefe);
        chefeId = chefe.getId();
        return chefe;
    }

    private BloqueioConta bloqueioAberto(UUID contaId, TipoUsuario tipo) {
        BloqueioConta b = new BloqueioConta();
        b.setContaId(contaId);
        b.setContaTipo(tipo);
        b.setCategoria(CategoriaConteudo.AMEACA);
        b.setTermo("x");
        b.setTexto("texto");
        b.setCriadoEm(LocalDateTime.now());
        return bloqueioRepository.save(b);
    }

    private Cookie cookieModerador() {
        String email = "moderacao@chefhome.test";
        return cookie(moderacaoRegistry.idDe(email), email, TipoUsuario.MODERADOR);
    }

    @Test
    void reverterDevolveLoginEAcoes() throws Exception {
        ChefeModel chefe = novoChefe();
        Cookie chefeCookie = cookie(chefeId, chefe.getEmail(), TipoUsuario.CHEFE);
        BloqueioConta bloqueio = bloqueioAberto(chefeId, TipoUsuario.CHEFE);
        mockMvc.perform(get("/chefe/me").cookie(chefeCookie)).andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/moderacao/bloqueios/" + bloqueio.getId() + "/reverter").cookie(cookieModerador()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/chefe/me").cookie(chefeCookie)).andExpect(status().isOk());
        assertThatCode(() -> loginService.loginGoogle(new GoogleUser(chefe.getEmail(), "Chef", null)))
                .doesNotThrowAnyException();
        assertThat(bloqueioRepository.findById(bloqueio.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusBloqueio.REVERTIDO);
    }

    @Test
    void confirmarMantemBloqueadoEInativaChef() throws Exception {
        ChefeModel chefe = novoChefe();
        BloqueioConta bloqueio = bloqueioAberto(chefeId, TipoUsuario.CHEFE);

        mockMvc.perform(patch("/moderacao/bloqueios/" + bloqueio.getId() + "/confirmar").cookie(cookieModerador()))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/moderacao/bloqueios/" + bloqueio.getId() + "/confirmar").cookie(cookieModerador()))
                .andExpect(status().isConflict());

        assertThat(chefeRepository.findById(chefeId).orElseThrow().getAtivo()).isFalse();
    }

    @Test
    void reverterUmDeDoisBloqueiosMantemAContaBloqueada() throws Exception {
        ChefeModel chefe = novoChefe();
        BloqueioConta primeiro = bloqueioAberto(chefeId, TipoUsuario.CHEFE);
        BloqueioConta segundo = bloqueioAberto(chefeId, TipoUsuario.CHEFE);

        mockMvc.perform(patch("/moderacao/bloqueios/" + primeiro.getId() + "/reverter").cookie(cookieModerador()))
                .andExpect(status().isNoContent());
        assertThat(chefeRepository.findById(chefeId).orElseThrow().getBloqueadoEm()).isNotNull();

        mockMvc.perform(patch("/moderacao/bloqueios/" + segundo.getId() + "/reverter").cookie(cookieModerador()))
                .andExpect(status().isNoContent());
        assertThat(chefeRepository.findById(chefeId).orElseThrow().getBloqueadoEm()).isNull();
    }

    @Test
    void filaListaAbertosParaModeracao() throws Exception {
        ChefeModel chefe = novoChefe();
        bloqueioAberto(chefeId, TipoUsuario.CHEFE);

        mockMvc.perform(get("/moderacao/bloqueios?status=ABERTO").cookie(cookieModerador()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.content[0].nome").value("Chef"));
    }

    @Test
    void filaSoParaModeracao() throws Exception {
        ChefeModel chefe = novoChefe();
        chefe.setBloqueadoEm(null);
        chefeRepository.save(chefe);
        Cookie chefeCookie = cookie(chefeId, chefe.getEmail(), TipoUsuario.CHEFE);
        UserModel cliente = new UserModel();
        cliente.setNome("Cliente");
        cliente.setEmail("cliente-" + UUID.randomUUID() + "@x.com");
        cliente.setEndereco(new ArrayList<>());
        cliente = usuarioRepository.save(cliente);
        clienteId = cliente.getId();

        mockMvc.perform(get("/moderacao/bloqueios").cookie(cookie(clienteId, cliente.getEmail(), TipoUsuario.CLIENTE)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/moderacao/bloqueios").cookie(chefeCookie)).andExpect(status().isNotFound());
    }

    private Cookie cookie(UUID id, String email, TipoUsuario tipo) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(new UsuarioAutenticado(id, email, tipo)));
    }
}
