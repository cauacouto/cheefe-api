package com.couto.chefe_api.security;

import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Conversa;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.Pratos;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.AvaliacaoRepository;
import com.couto.chefe_api.repository.BloqueioContaRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.ConversaRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.MensagemRepository;
import com.couto.chefe_api.repository.NotificacaoRepository;
import com.couto.chefe_api.repository.PratosRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verificação de telefone parada (sem provedor; ver README "Telefone verificado"): sem telefone verificado tudo
 * funciona como antes da V12, e o chef aparece normalmente.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(com.couto.chefe_api.FiltroDeTeste.class)
class TelefoneParadoIntegrationTest {

    static final LocalDateTime FUTURO = LocalDateTime.now().plusDays(10).withHour(18).withMinute(0).withSecond(0).withNano(0);
    static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired ConversaRepository conversaRepository;
    @Autowired MensagemRepository mensagemRepository;
    @Autowired AvaliacaoRepository avaliacaoRepository;
    @Autowired PratosRepository pratosRepository;
    @Autowired com.couto.chefe_api.MenuDeTeste menus;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired BloqueioContaRepository bloqueioContaRepository;

    MockMvc mockMvc;
    /** Com telefone verificado. */
    UserModel ana;
    ChefeModel bia;
    /** Sem telefone verificado. */
    UserModel caio;
    ChefeModel duda;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ana = usuarioRepository.save(TelefoneDeTeste.verificado(cliente("Ana")));
        caio = usuarioRepository.save(cliente("Caio"));
        bia = chefeRepository.save(TelefoneDeTeste.verificado(chefe("Bia")));
        duda = chefeRepository.save(chefe("Duda"));
        menus.ativo(bia); // sem menu ativo o chef não é visível (busca, destaque, agenda)
        menus.ativo(duda);
    }

    @AfterEach
    void limparBanco() {
        notificacaoRepository.deleteAll();
        bloqueioContaRepository.deleteAll();
        mensagemRepository.deleteAll();
        conversaRepository.deleteAll();
        agendamentoRepository.deleteAll();
        pratosRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void clienteSemTelefoneAgendaEChefSemTelefonePodeSerAgendado() throws Exception {
        agendar(caio, endereco(caio), bia).andExpect(status().isCreated());
        agendar(ana, endereco(ana), duda).andExpect(status().isCreated());
    }

    @Test
    void semTelefoneAbreConversaEEnviaMensagem() throws Exception {
        mockMvc.perform(post("/conversas").cookie(cookie(caio)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"chefeId\":\"" + bia.getId() + "\"}")).andExpect(status().isCreated());
        Conversa daDuda = conversa(ana, duda);

        enviar(cookie(duda), daDuda, "oi").andExpect(status().isCreated());
    }

    @Test
    void quemNaoParticipaContinuaRecebendo404() throws Exception {
        Conversa alheia = conversa(ana, bia);

        enviar(cookie(caio), alheia, "oi").andExpect(status().isNotFound());
    }

    @Test
    void odioDeContaSemTelefoneContinuaBloqueandoAConta() throws Exception {
        Conversa doCaio = conversa(caio, bia);

        enviar(cookie(caio), doCaio, "seu odiento").andExpect(status().isForbidden());

        assertThat(usuarioRepository.findById(caio.getId()).orElseThrow().getBloqueadoEm()).isNotNull();
    }

    @Test
    void semTelefoneAvalia() throws Exception {
        Agendamento doCaio = concluido(agendamento(caio, bia));
        Agendamento daDuda = concluido(agendamento(ana, duda));

        avaliar(cookie(caio), doCaio, "{\"chefeId\":\"" + bia.getId() + "\",\"nota\":5}").andExpect(status().isCreated());
        avaliar(cookie(duda), daDuda, "{\"nota\":5}").andExpect(status().isCreated());
    }

    @Test
    void chefSemTelefoneApareceNaBuscaNoDestaqueNosPratosENaAgenda() throws Exception {
        prato(bia, "Moqueca");
        prato(duda, "Feijoada");

        mockMvc.perform(get("/chefe").cookie(cookie(ana)))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(bia.getId().toString(), duda.getId().toString())));
        mockMvc.perform(get("/chefe/destaque").cookie(cookie(ana)))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(bia.getId().toString(), duda.getId().toString())));
        mockMvc.perform(get("/pratos").cookie(cookie(ana)))
                .andExpect(jsonPath("$.content[*].nomePrato", containsInAnyOrder("Moqueca", "Feijoada", "Prato do menu", "Prato do menu")));
        mockMvc.perform(get("/chefe/" + duda.getId() + "/agenda").cookie(cookie(ana))).andExpect(status().isOk());
    }

    @Test
    void telefoneVerificadoEhSoInformacao() throws Exception {
        mockMvc.perform(get("/auth/me").cookie(cookie(caio))).andExpect(jsonPath("$.telefoneVerificado").value(false));
        mockMvc.perform(get("/auth/me").cookie(cookie(ana))).andExpect(jsonPath("$.telefoneVerificado").value(true));
        // sem exigência, o telefone não é pendência do perfil
        mockMvc.perform(get("/chefe/me").cookie(cookie(duda)))
                .andExpect(jsonPath("$.telefoneVerificado").value(false))
                .andExpect(jsonPath("$.perfilCompleto").value(true))
                .andExpect(jsonPath("$.camposPendentes").isEmpty());
    }

    ResultActions agendar(UserModel cliente, EnderecoModel endereco, ChefeModel chefe) throws Exception {
        return mockMvc.perform(post("/agendamentos").cookie(cookie(cliente)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuId\":" + menus.ativo(chefe).getId() + ",\"dataHoraInicial\":\"" + FUTURO.format(FORMATO)
                        + "\",\"enderecoId\":" + endereco.getId()
                        + ",\"ocasiao\":\"FAMILIA\",\"quantidadePessoas\":2}"));
    }

    ResultActions enviar(Cookie quem, Conversa conversa, String texto) throws Exception {
        return mockMvc.perform(post("/conversas/" + conversa.getId() + "/mensagens").cookie(quem)
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"" + texto + "\"}"));
    }

    ResultActions avaliar(Cookie quem, Agendamento agendamento, String corpo) throws Exception {
        return mockMvc.perform(post("/agendamentos/" + agendamento.getId() + "/avaliacoes").cookie(quem)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    Conversa conversa(UserModel cliente, ChefeModel chefe) {
        Conversa conversa = new Conversa();
        conversa.setCliente(cliente);
        conversa.setChefe(chefe);
        conversa.setCriadaEm(LocalDateTime.now());
        conversa.setUltimaMensagemEm(LocalDateTime.now());
        return conversaRepository.save(conversa);
    }

    Agendamento agendamento(UserModel cliente, ChefeModel chefe) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(endereco(cliente));
        agendamento.setChefe(chefe);
        agendamento.setDataHoraInicial(FUTURO);
        agendamento.setDataHoraFinal(FUTURO.plusHours(2));
        agendamento.setQuantidadePessoas(2);
        agendamento.setValorTotal(new BigDecimal("200.00"));
        agendamento.setCriadoEm(LocalDateTime.now());
        return agendamentoRepository.save(agendamento);
    }

    Agendamento concluido(Agendamento agendamento) {
        agendamento.setDataHoraInicial(LocalDateTime.now().minusDays(2));
        agendamento.setDataHoraFinal(LocalDateTime.now().minusDays(2).plusHours(2));
        agendamento.setStatus(StatusAgendamento.CONCLUIDO);
        agendamento.setConcluidoEm(LocalDateTime.now().minusDays(1));
        return agendamentoRepository.save(agendamento);
    }

    EnderecoModel endereco(UserModel dono) {
        EnderecoModel endereco = new EnderecoModel();
        endereco.setRua("Rua A");
        endereco.setNumero("1");
        endereco.setBairro("Centro");
        endereco.setCidade("São Paulo");
        endereco.setEstado("SP");
        endereco.setCep("01001-000");
        endereco.setTipoDeResidencia(TipoDeResidencia.CASA);
        endereco.setUsuario(dono);
        return enderecoRepository.save(endereco);
    }

    void prato(ChefeModel chefe, String nome) {
        Pratos prato = new Pratos();
        prato.setNomePrato(nome);
        prato.setDescricao("Prato " + nome);
        prato.setImageUrl("https://img.test/" + nome + ".jpg");
        prato.setChefeModel(chefe);
        pratosRepository.save(prato);
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
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setEspecialidades(Set.of(Especialidades.BRASILEIRA));
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        return chefe;
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
