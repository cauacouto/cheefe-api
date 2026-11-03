package com.couto.chefe_api.security;

import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.TelefoneBanido;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.TelefoneBanidoRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.service.HashTelefone;
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
import java.time.LocalDateTime;
import java.util.Set;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /telefone/codigo e /telefone/verificar com o verificador falso (código 000000). */
@SpringBootTest
@ActiveProfiles("test")
class TelefoneIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired TelefoneBanidoRepository telefoneBanidoRepository;
    @Autowired HashTelefone hashTelefone;
    @Autowired ModeracaoRegistry moderacaoRegistry;

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
        telefoneBanidoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void fluxoCompletoDoClienteGravaOTelefoneVerificado() throws Exception {
        String numero = TelefoneDeTeste.novoNumero();
        String digitado = "(" + numero.substring(3, 5) + ") " + numero.substring(5, 10) + "-" + numero.substring(10);

        pedir(cookie(ana), digitado).andExpect(status().isAccepted());
        verificar(cookie(ana), digitado, "000000").andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value(numero))
                .andExpect(jsonPath("$.verificadoEm").isNotEmpty());

        UserModel salvo = usuarioRepository.findById(ana.getId()).orElseThrow();
        assertThat(salvo.getTelefoneVerificado()).isEqualTo(numero);
        assertThat(salvo.getTelefoneVerificadoEm()).isNotNull();
        // o telefone de contato livre continua separado
        assertThat(salvo.getTelefone()).isNull();
    }

    @Test
    void fluxoCompletoDoChef() throws Exception {
        String numero = TelefoneDeTeste.novoNumero();
        pedir(cookie(bia), numero).andExpect(status().isAccepted());
        verificar(cookie(bia), numero, "000000").andExpect(status().isOk()).andExpect(jsonPath("$.telefone").value(numero));

        assertThat(chefeRepository.findById(bia.getId()).orElseThrow().getTelefoneVerificado()).isEqualTo(numero);
    }

    @Test
    void foraDoFormatoDa400() throws Exception {
        for (String invalido : new String[]{"1187654321", "+1 2025550123", "abc", ""}) {
            pedir(cookie(ana), invalido).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Telefone inválido"));
            verificar(cookie(ana), invalido, "000000").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Telefone inválido"));
        }
        mockMvc.perform(post("/telefone/codigo").cookie(cookie(ana)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void telefoneDeOutraContaDa409NosDoisPassos() throws Exception {
        UserModel caio = novoCliente("Caio");
        String numero = verificarDireto(caio);

        // de outro cliente e de um chef (a unicidade vale entre as duas tabelas)
        pedir(cookie(ana), numero).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Telefone já usado por outra conta"));
        pedir(cookie(bia), numero).andExpect(status().isConflict());
        verificar(cookie(ana), numero, "000000").andExpect(status().isConflict());
        verificar(cookie(bia), numero, "000000").andExpect(status().isConflict());

        assertThat(usuarioRepository.findById(ana.getId()).orElseThrow().getTelefoneVerificado()).isNull();
        assertThat(chefeRepository.findById(bia.getId()).orElseThrow().getTelefoneVerificado()).isNull();
    }

    @Test
    void outraContaVerificaNoMeioEntreOPedidoEAVerificacao() throws Exception {
        String numero = TelefoneDeTeste.novoNumero();
        pedir(cookie(ana), numero).andExpect(status().isAccepted());
        ChefeModel rapido = novoChefe("Rápido");
        rapido.setTelefoneVerificado(numero);
        chefeRepository.save(rapido);

        verificar(cookie(ana), numero, "000000").andExpect(status().isConflict());
    }

    @Test
    void telefoneBanidoDa403() throws Exception {
        String numero = TelefoneDeTeste.novoNumero();
        TelefoneBanido banido = new TelefoneBanido();
        banido.setHash(hashTelefone.de(numero));
        banido.setBanidoEm(java.time.LocalDate.now());
        telefoneBanidoRepository.save(banido);

        pedir(cookie(ana), numero).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Telefone não pode ser usado"));
        verificar(cookie(bia), numero, "000000").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Telefone não pode ser usado"));
    }

    @Test
    void novoPedidoEmMenosDe60SegundosDa429SoParaAMesmaConta() throws Exception {
        pedir(cookie(ana), TelefoneDeTeste.novoNumero()).andExpect(status().isAccepted());
        pedir(cookie(ana), TelefoneDeTeste.novoNumero()).andExpect(status().isTooManyRequests());

        pedir(cookie(bia), TelefoneDeTeste.novoNumero()).andExpect(status().isAccepted());
    }

    @Test
    void naoDaParaSondarNumerosBanidosOuEmUso() throws Exception {
        String emUso = verificarDireto(novoCliente("Caio"));

        // número de outra conta também conta para o intervalo: um pedido por minuto, como os aceitos
        pedir(cookie(ana), emUso).andExpect(status().isConflict());
        pedir(cookie(ana), TelefoneDeTeste.novoNumero()).andExpect(status().isTooManyRequests());
        // sem o código certo, verificar não revela se o número é de outra conta
        verificar(cookie(bia), emUso, "123456").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void formatoInvalidoNaoContaParaOIntervalo() throws Exception {
        pedir(cookie(ana), "123").andExpect(status().isBadRequest());
        pedir(cookie(ana), TelefoneDeTeste.novoNumero()).andExpect(status().isAccepted());
    }

    @Test
    void codigoErradoDa422ENaoGrava() throws Exception {
        String numero = TelefoneDeTeste.novoNumero();
        pedir(cookie(ana), numero).andExpect(status().isAccepted());

        verificar(cookie(ana), numero, "123456").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Código inválido ou expirado"));
        verificar(cookie(ana), numero, "abc").andExpect(status().isUnprocessableContent());

        assertThat(usuarioRepository.findById(ana.getId()).orElseThrow().getTelefoneVerificado()).isNull();
    }

    @Test
    void verificarOutroNumeroSubstituiEOAntigoFicaLivre() throws Exception {
        String primeiro = verificarDireto(ana);
        String segundo = TelefoneDeTeste.novoNumero();

        verificar(cookie(ana), segundo, "000000").andExpect(status().isOk());
        assertThat(usuarioRepository.findById(ana.getId()).orElseThrow().getTelefoneVerificado()).isEqualTo(segundo);

        verificar(cookie(bia), primeiro, "000000").andExpect(status().isOk());
    }

    @Test
    void verificarDeNovoOProprioNumeroNaoEhConflito() throws Exception {
        String numero = verificarDireto(ana);

        pedir(cookie(ana), numero).andExpect(status().isAccepted());
        verificar(cookie(ana), numero, "000000").andExpect(status().isOk());
    }

    @Test
    void moderadorDa403ESemLoginDa401() throws Exception {
        String email = "moderacao@chefhome.test";
        Cookie moderador = new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(moderacaoRegistry.idDe(email), email, TipoUsuario.MODERADOR)));
        String numero = TelefoneDeTeste.novoNumero();

        pedir(moderador, numero).andExpect(status().isForbidden());
        verificar(moderador, numero, "000000").andExpect(status().isForbidden());
        mockMvc.perform(post("/telefone/codigo").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("telefone", numero)))).andExpect(status().isUnauthorized());
    }

    @Test
    void chefInativoDa403() throws Exception {
        bia.setAtivo(false);
        chefeRepository.save(bia);
        String numero = TelefoneDeTeste.novoNumero();

        pedir(cookie(bia), numero).andExpect(status().isForbidden());
        verificar(cookie(bia), numero, "000000").andExpect(status().isForbidden());
        assertThat(chefeRepository.findById(bia.getId()).orElseThrow().getTelefoneVerificado()).isNull();
    }

    /** Verifica pelo fluxo de verdade e devolve o número. */
    String verificarDireto(UserModel cliente) throws Exception {
        String numero = TelefoneDeTeste.novoNumero();
        verificar(cookie(cliente), numero, "000000").andExpect(status().isOk());
        return numero;
    }

    ResultActions pedir(Cookie quem, String telefone) throws Exception {
        return mockMvc.perform(post("/telefone/codigo").cookie(quem).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("telefone", telefone))));
    }

    ResultActions verificar(Cookie quem, String telefone, String codigo) throws Exception {
        return mockMvc.perform(post("/telefone/verificar").cookie(quem).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("telefone", telefone, "codigo", codigo))));
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
        chefe.setEmail(UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setEspecialidades(Set.of(Especialidades.BRASILEIRA));
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
