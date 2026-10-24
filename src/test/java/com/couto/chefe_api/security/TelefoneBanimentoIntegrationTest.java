package com.couto.chefe_api.security;

import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.domain.BloqueioConta;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.CategoriaConteudo;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AcaoModeracaoRepository;
import com.couto.chefe_api.repository.BloqueioContaRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.TelefoneBanidoRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.service.HashTelefone;
import com.couto.chefe_api.service.ModeracaoService;
import com.couto.chefe_api.service.UsuarioService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O telefone de quem teve o banimento confirmado não serve para outra conta; conta apagada libera o número. */
@SpringBootTest
@ActiveProfiles("test")
class TelefoneBanimentoIntegrationTest {

    static final String MODERADOR = "moderacao@chefhome.test";

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired BloqueioContaRepository bloqueioContaRepository;
    @Autowired AcaoModeracaoRepository acaoModeracaoRepository;
    @Autowired TelefoneBanidoRepository telefoneBanidoRepository;
    @Autowired ModeracaoService moderacaoService;
    @Autowired UsuarioService usuarioService;
    @Autowired HashTelefone hashTelefone;

    MockMvc mockMvc;
    /** Conta nova tentando usar o número. */
    UserModel nova;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        nova = usuarioRepository.save(cliente("Nova"));
    }

    @AfterEach
    void limparBanco() {
        acaoModeracaoRepository.deleteAll();
        bloqueioContaRepository.deleteAll();
        telefoneBanidoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void confirmarBloqueioDeClienteGravaOHashEBloqueiaONumeroMesmoDepoisDaContaApagada() throws Exception {
        UserModel banido = usuarioRepository.save(TelefoneDeTeste.verificado(cliente("Banido")));
        String numero = banido.getTelefoneVerificado();

        moderacaoService.confirmarBloqueio(bloqueio(banido.getId(), TipoUsuario.CLIENTE).getId(), MODERADOR);

        assertThat(telefoneBanidoRepository.findAll()).singleElement().satisfies(t -> {
            assertThat(t.getHash()).isEqualTo(hashTelefone.de(numero)).doesNotContain(numero.substring(3));
            assertThat(t.getBanidoEm()).isNotNull();
        });
        verificar(nova, numero).andExpect(status().isForbidden());

        usuarioService.deletar(banido.getId());

        pedir(nova, numero).andExpect(status().isForbidden());
        verificar(nova, numero).andExpect(status().isForbidden());
        assertThat(usuarioRepository.findById(nova.getId()).orElseThrow().getTelefoneVerificado()).isNull();
    }

    @Test
    void confirmarBloqueioDeChefGravaOHashEOSoftDeleteNaoLibera() throws Exception {
        ChefeModel banido = chefeRepository.save(TelefoneDeTeste.verificado(chefe("Banido")));
        String numero = banido.getTelefoneVerificado();

        moderacaoService.confirmarBloqueio(bloqueio(banido.getId(), TipoUsuario.CHEFE).getId(), MODERADOR);
        // a sessão ainda aberta (bloqueada, mas com token válido) não consegue nada: o hash já está gravado
        chefeRepository.findById(banido.getId()).ifPresent(c -> {
            c.setTelefoneVerificado(null);
            chefeRepository.save(c);
        });

        assertThat(telefoneBanidoRepository.existsById(hashTelefone.de(numero))).isTrue();
        verificar(nova, numero).andExpect(status().isForbidden());
    }

    @Test
    void reverterBloqueioNaoGrava() throws Exception {
        UserModel suspenso = usuarioRepository.save(TelefoneDeTeste.verificado(cliente("Suspenso")));

        moderacaoService.reverterBloqueio(bloqueio(suspenso.getId(), TipoUsuario.CLIENTE).getId(), MODERADOR);

        assertThat(telefoneBanidoRepository.count()).isZero();
    }

    @Test
    void confirmarBloqueioDeContaSemTelefoneNaoGrava() {
        UserModel semTelefone = usuarioRepository.save(cliente("Sem"));

        moderacaoService.confirmarBloqueio(bloqueio(semTelefone.getId(), TipoUsuario.CLIENTE).getId(), MODERADOR);

        assertThat(telefoneBanidoRepository.count()).isZero();
    }

    @Test
    void banirChefGravaReativarApagaEOMesmoHashDuasVezesNaoEhErro() throws Exception {
        ChefeModel chefe = chefeRepository.save(TelefoneDeTeste.verificado(chefe("Chef")));
        String numero = chefe.getTelefoneVerificado();

        moderacaoService.banir(chefe.getId(), MODERADOR);
        moderacaoService.banir(chefe.getId(), MODERADOR);

        assertThat(telefoneBanidoRepository.findAll()).singleElement()
                .satisfies(t -> assertThat(t.getHash()).isEqualTo(hashTelefone.de(numero)));
        pedir(nova, numero).andExpect(status().isForbidden());

        moderacaoService.reativar(chefe.getId(), MODERADOR);

        assertThat(telefoneBanidoRepository.count()).isZero();
        // o número continua na conta do chef: para outra conta ele agora é só "de outra conta"
        // (outra conta nova: a primeira já pediu há menos de 60 s, e pedido recusado também conta para o intervalo)
        pedir(usuarioRepository.save(cliente("Outra")), numero).andExpect(status().isConflict());
    }

    @Test
    void clienteApagadoLiberaOTelefone() throws Exception {
        UserModel saindo = usuarioRepository.save(TelefoneDeTeste.verificado(cliente("Saindo")));
        String numero = saindo.getTelefoneVerificado();

        mockMvc.perform(delete("/usuario/me").cookie(cookie(saindo.getId(), saindo.getEmail(), TipoUsuario.CLIENTE)))
                .andExpect(status().is2xxSuccessful());

        verificar(nova, numero).andExpect(status().isOk());
    }

    @Test
    void chefApagadoZeraOTelefoneELiberaONumero() throws Exception {
        ChefeModel saindo = chefeRepository.save(TelefoneDeTeste.verificado(chefe("Saindo")));
        String numero = saindo.getTelefoneVerificado();

        mockMvc.perform(delete("/chefe/me").cookie(cookie(saindo.getId(), saindo.getEmail(), TipoUsuario.CHEFE)))
                .andExpect(status().is2xxSuccessful());

        ChefeModel apagado = chefeRepository.findById(saindo.getId()).orElseThrow();
        assertThat(apagado.getTelefoneVerificado()).isNull();
        assertThat(apagado.getTelefoneVerificadoEm()).isNull();
        verificar(nova, numero).andExpect(status().isOk());
    }

    BloqueioConta bloqueio(UUID contaId, TipoUsuario tipo) {
        BloqueioConta bloqueio = new BloqueioConta();
        bloqueio.setContaId(contaId);
        bloqueio.setContaTipo(tipo);
        bloqueio.setCategoria(CategoriaConteudo.ODIO);
        bloqueio.setTermo("odiento");
        bloqueio.setTexto("seu odiento");
        bloqueio.setCriadoEm(LocalDateTime.now());
        if (tipo == TipoUsuario.CLIENTE) {
            usuarioRepository.findById(contaId).ifPresent(u -> {
                u.setBloqueadoEm(LocalDateTime.now());
                usuarioRepository.save(u);
            });
        } else {
            chefeRepository.findById(contaId).ifPresent(c -> {
                c.setBloqueadoEm(LocalDateTime.now());
                chefeRepository.save(c);
            });
        }
        return bloqueioContaRepository.save(bloqueio);
    }

    ResultActions pedir(UserModel quem, String telefone) throws Exception {
        return mockMvc.perform(post("/telefone/codigo").cookie(cookie(quem.getId(), quem.getEmail(), TipoUsuario.CLIENTE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"telefone\":\"" + telefone + "\"}"));
    }

    ResultActions verificar(UserModel quem, String telefone) throws Exception {
        return mockMvc.perform(post("/telefone/verificar").cookie(cookie(quem.getId(), quem.getEmail(), TipoUsuario.CLIENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefone\":\"" + telefone + "\",\"codigo\":\"000000\"}"));
    }

    static UserModel cliente(String nome) {
        UserModel usuario = new UserModel();
        usuario.setNome(nome);
        usuario.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        usuario.setEndereco(new ArrayList<>());
        return usuario;
    }

    static ChefeModel chefe(String nome) {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome(nome);
        chefe.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("100"));
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        return chefe;
    }

    Cookie cookie(UUID id, String email, TipoUsuario tipo) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(new UsuarioAutenticado(id, email, tipo)));
    }
}
