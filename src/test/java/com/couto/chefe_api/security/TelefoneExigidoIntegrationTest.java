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

/** Sem telefone verificado: as ações listadas no spec dão 403 TELEFONE_NAO_VERIFICADO e o chef some. */
@SpringBootTest
@ActiveProfiles("test")
@Import(com.couto.chefe_api.FiltroDeTeste.class)
class TelefoneExigidoIntegrationTest {

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
    void clienteSemTelefoneNaoAgenda() throws Exception {
        exigeTelefone(agendar(caio, endereco(caio), bia));
        assertThat(agendamentoRepository.count()).isZero();

        agendar(ana, endereco(ana), bia).andExpect(status().isCreated());
    }

    @Test
    void clienteSemTelefoneNaoAbreConversa() throws Exception {
        exigeTelefone(mockMvc.perform(post("/conversas").cookie(cookie(caio)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"chefeId\":\"" + bia.getId() + "\"}")));
        assertThat(conversaRepository.count()).isZero();
    }

    @Test
    void semTelefoneNinguemEnviaMensagem() throws Exception {
        Conversa doCaio = conversa(caio, bia);
        Conversa daDuda = conversa(ana, duda);

        exigeTelefone(enviar(cookie(caio), doCaio, "oi"));
        exigeTelefone(enviar(cookie(duda), daDuda, "oi"));
        assertThat(mensagemRepository.count()).isZero();

        enviar(cookie(bia), doCaio, "oi").andExpect(status().isCreated());
        enviar(cookie(ana), daDuda, "oi").andExpect(status().isCreated());
    }

    @Test
    void quemNaoParticipaContinuaRecebendo404AntesDaExigencia() throws Exception {
        Conversa alheia = conversa(ana, bia);

        enviar(cookie(caio), alheia, "oi").andExpect(status().isNotFound());
    }

    @Test
    void odioDeContaSemTelefoneNaoChegaAoFiltroNemBloqueiaAConta() throws Exception {
        Conversa doCaio = conversa(caio, bia);

        exigeTelefone(enviar(cookie(caio), doCaio, "seu odiento"));

        assertThat(bloqueioContaRepository.count()).isZero();
        assertThat(usuarioRepository.findById(caio.getId()).orElseThrow().getBloqueadoEm()).isNull();
    }

    @Test
    void semTelefoneNinguemAvalia() throws Exception {
        Agendamento doCaio = concluido(agendamento(caio, bia));
        Agendamento daDuda = concluido(agendamento(ana, duda));

        exigeTelefone(avaliar(cookie(caio), doCaio, "{\"chefeId\":\"" + bia.getId() + "\",\"nota\":5}"));
        exigeTelefone(avaliar(cookie(duda), daDuda, "{\"nota\":5}"));
        assertThat(avaliacaoRepository.count()).isZero();

        avaliar(cookie(bia), doCaio, "{\"nota\":4}").andExpect(status().isCreated());
    }

    @Test
    void chefSemTelefoneSomeDaBuscaDoDestaqueEDosPratos() throws Exception {
        prato(bia, "Moqueca");
        prato(duda, "Feijoada");

        mockMvc.perform(get("/chefe").cookie(cookie(ana)))
                .andExpect(jsonPath("$.content[*].id", contains(bia.getId().toString())));
        mockMvc.perform(get("/chefe/destaque").cookie(cookie(ana)))
                .andExpect(jsonPath("$.content[*].id", contains(bia.getId().toString())));
        mockMvc.perform(get("/pratos").cookie(cookie(ana)))
                .andExpect(jsonPath("$.content[*].nomePrato", contains("Moqueca")));
        mockMvc.perform(get("/pratos/chefe/" + duda.getId()).cookie(cookie(ana)))
                .andExpect(jsonPath("$.length()").value(0));
        // o próprio chef continua vendo os pratos dele
        mockMvc.perform(get("/pratos/meus").cookie(cookie(duda)))
                .andExpect(jsonPath("$[*].nomePrato", containsInAnyOrder("Feijoada")));
    }

    @Test
    void oFrontSabeQuandoPedirOTelefone() throws Exception {
        mockMvc.perform(get("/auth/me").cookie(cookie(caio))).andExpect(jsonPath("$.telefoneVerificado").value(false));
        mockMvc.perform(get("/auth/me").cookie(cookie(ana))).andExpect(jsonPath("$.telefoneVerificado").value(true));
        mockMvc.perform(get("/auth/me").cookie(cookie(duda))).andExpect(jsonPath("$.telefoneVerificado").value(false));
        // o chef sem telefone não aparece na busca: o perfil diz isso, em vez de "completo"
        mockMvc.perform(get("/chefe/me").cookie(cookie(duda)))
                .andExpect(jsonPath("$.telefoneVerificado").value(false))
                .andExpect(jsonPath("$.perfilCompleto").value(false))
                .andExpect(jsonPath("$.camposPendentes", contains("telefoneVerificado")));
        mockMvc.perform(get("/chefe/me").cookie(cookie(bia)))
                .andExpect(jsonPath("$.telefoneVerificado").value(true))
                .andExpect(jsonPath("$.perfilCompleto").value(true))
                .andExpect(jsonPath("$.camposPendentes").isEmpty());
    }

    @Test
    void agendaPublicaDeChefSemTelefoneDa404ComoABusca() throws Exception {
        mockMvc.perform(get("/chefe/" + duda.getId() + "/agenda").cookie(cookie(ana))).andExpect(status().isNotFound());
        mockMvc.perform(get("/chefe/" + bia.getId() + "/agenda").cookie(cookie(ana))).andExpect(status().isOk());
    }

    @Test
    void chefSemTelefoneNaoPodeSerAgendado() throws Exception {
        agendar(ana, endereco(ana), duda).andExpect(status().isConflict());
        agendar(ana, endereco(ana), bia, duda).andExpect(status().isConflict());
        assertThat(agendamentoRepository.count()).isZero();
    }

    @Test
    void oQueJaExisteContinuaSemExigir() throws Exception {
        Agendamento pendente = agendamento(caio, duda);
        Conversa conversa = conversa(caio, duda);

        mockMvc.perform(get("/agendamentos/meus").cookie(cookie(caio))).andExpect(status().isOk());
        mockMvc.perform(get("/agendamentos/recebidos").cookie(cookie(duda))).andExpect(status().isOk());
        mockMvc.perform(get("/conversas/" + conversa.getId() + "/mensagens").cookie(cookie(caio))).andExpect(status().isOk());
        mockMvc.perform(patch("/conversas/" + conversa.getId() + "/lidas").cookie(cookie(duda))).andExpect(status().isNoContent());
        mockMvc.perform(patch("/agendamentos/" + pendente.getId() + "/confirmar").cookie(cookie(duda))).andExpect(status().isOk());
        mockMvc.perform(patch("/agendamentos/" + pendente.getId() + "/cancelar").cookie(cookie(caio))).andExpect(status().isOk());

        assertThat(agendamentoRepository.findById(pendente.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.CANCELADO);
    }

    void exigeTelefone(ResultActions resposta) throws Exception {
        resposta.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("TELEFONE_NAO_VERIFICADO"))
                .andExpect(jsonPath("$.detail").value("Verifique seu telefone para continuar"));
    }

    ResultActions agendar(UserModel cliente, EnderecoModel endereco, ChefeModel... chefes) throws Exception {
        String ids = String.join(",", java.util.Arrays.stream(chefes).map(c -> "\"" + c.getId() + "\"").toList());
        return mockMvc.perform(post("/agendamentos").cookie(cookie(cliente)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataHoraInicial\":\"" + FUTURO.format(FORMATO) + "\",\"dataHoraFinal\":\""
                        + FUTURO.plusHours(2).format(FORMATO) + "\",\"enderecoId\":" + endereco.getId()
                        + ",\"chefeIds\":[" + ids + "],\"quantidadePessoas\":2}"));
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

    Agendamento agendamento(UserModel cliente, ChefeModel... chefes) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(endereco(cliente));
        agendamento.setChefes(new ArrayList<>(List.of(chefes)));
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
        chefe.setValorHora(new BigDecimal("100"));
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
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
