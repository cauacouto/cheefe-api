package com.couto.chefe_api.security;

import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.Pratos;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.domain.Avaliacao;
import com.couto.chefe_api.enums.CategoriaConteudo;
import com.couto.chefe_api.repository.AvaliacaoRepository;
import com.couto.chefe_api.repository.BloqueioContaRepository;
import com.couto.chefe_api.repository.NotificacaoRepository;
import com.couto.chefe_api.service.FiltroConteudo;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import java.util.Map;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.PratosRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Avaliar depois de um agendamento concluído (spec avaliações: "Quem avalia", "Média e anonimato"). */
@SpringBootTest
@ActiveProfiles("test")
class AvaliacaoIntegrationTest {

    static final LocalDateTime BASE = LocalDateTime.now().minusDays(3).withHour(18).withMinute(0).withSecond(0).withNano(0);

    /** Termos fictícios: o teste não depende da lista de produção. */
    @TestConfiguration
    static class FiltroDeTeste {
        @Bean
        @Primary
        FiltroConteudo filtroDeTeste() {
            return new FiltroConteudo(Map.of(
                    CategoriaConteudo.OFENSA, List.of("palavrao"),
                    CategoriaConteudo.ODIO, List.of("odiento"),
                    CategoriaConteudo.AMEACA, List.of("vou te pegar")));
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired PratosRepository pratosRepository;
    @Autowired AvaliacaoRepository avaliacaoRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired BloqueioContaRepository bloqueioContaRepository;
    @Autowired org.springframework.transaction.support.TransactionTemplate transacao;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void limparBanco() {
        notificacaoRepository.deleteAll();
        bloqueioContaRepository.deleteAll();
        agendamentoRepository.deleteAll();
        pratosRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void clienteAvaliaChefAnonimoESomaNoContador() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));

        avaliar(cookieDe(ana), a, "{\"chefeId\":\"" + bia.getId() + "\",\"nota\":5,"
                + "\"tags\":[\"PONTUAL\",\"COMIDA_EXCELENTE\"],\"comentario\":\"  Maravilhoso  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());

        Avaliacao salva = unica();
        assertThat(salva.getNota()).isEqualTo(5);
        java.util.Set<String> tags = transacao.execute(
                st -> new java.util.HashSet<>(avaliacaoRepository.findById(salva.getId()).orElseThrow().getTags()));
        assertThat(tags).containsExactlyInAnyOrder("PONTUAL", "COMIDA_EXCELENTE");
        assertThat(salva.getComentario()).isEqualTo("Maravilhoso");
        assertThat(salva.isAnonimo()).isTrue();
        assertThat(salva.getPublicadoEm()).isNotNull();
        ChefeModel depois = chefeRepository.findById(bia.getId()).orElseThrow();
        assertThat(depois.getSomaNotas()).isEqualTo(5);
        assertThat(depois.getTotalAvaliacoes()).isEqualTo(1);
        assertThat(notificacaoRepository.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getDestinatarioId()).isEqualTo(bia.getId());
            assertThat(n.getEvento().name()).isEqualTo("AVALIACAO_RECEBIDA");
            assertThat(n.getMensagem()).isEqualTo("Você recebeu uma nova avaliação");
            // sem apontar o agendamento: senão o chef saberia quem avaliou
            assertThat(n.getReferenciaId()).isNull();
        });
    }

    @Test
    void comentarioComNomeEsperaOChefAvaliarOCliente() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));

        avaliar(cookieDe(ana), a, "{\"chefeId\":\"" + bia.getId() + "\",\"nota\":4,\"comentario\":\"Gostei\","
                + "\"anonimo\":false}").andExpect(status().isCreated());
        assertThat(unica().getPublicadoEm()).isNull();

        avaliar(cookieDe(bia), a, "{\"nota\":5,\"tags\":[\"EDUCADO\"]}").andExpect(status().isCreated());

        Avaliacao daAna = avaliacaoRepository.findAll().stream().filter(x -> x.getAutorId().equals(ana.getId()))
                .findFirst().orElseThrow();
        assertThat(daAna.getPublicadoEm()).isNotNull();
        UserModel anaDepois = usuarioRepository.findById(ana.getId()).orElseThrow();
        assertThat(anaDepois.getSomaNotas()).isEqualTo(5);
        assertThat(anaDepois.getTotalAvaliacoes()).isEqualTo(1);
    }

    @Test
    void semComentarioEhPublicadaEAnonimaMesmoPedindoNome() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));

        avaliar(cookieDe(ana), a, "{\"chefeId\":\"" + bia.getId() + "\",\"nota\":3,\"anonimo\":false}")
                .andExpect(status().isCreated());

        assertThat(unica().isAnonimo()).isTrue();
        assertThat(unica().getPublicadoEm()).isNotNull();
    }

    @Test
    void prazoEStatus() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        String corpo = "{\"chefeId\":\"" + bia.getId() + "\",\"nota\":5}";

        Agendamento seisDias = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(6));
        avaliar(cookieDe(ana), seisDias, corpo).andExpect(status().isCreated());

        Agendamento vencido = concluido(novoAgendamento(ana, BASE.minusDays(1), bia),
                LocalDateTime.now().minusDays(7).minusMinutes(1));
        avaliar(cookieDe(ana), vencido, corpo).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O prazo para avaliar (7 dias depois da conclusão) já passou"));

        Agendamento semData = concluido(novoAgendamento(ana, BASE.minusDays(2), bia), null);
        avaliar(cookieDe(ana), semData, corpo).andExpect(status().isConflict());

        Agendamento confirmado = novoAgendamento(ana, BASE.minusDays(3), bia);
        confirmado.setStatus(StatusAgendamento.CONFIRMADO);
        agendamentoRepository.save(confirmado);
        avaliar(cookieDe(ana), confirmado, corpo).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Só é possível avaliar agendamentos concluídos"));
    }

    @Test
    void validacoes() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        String chef = "\"chefeId\":\"" + bia.getId() + "\",";

        avaliar(cookieDe(ana), a, "{" + chef + "\"nota\":0}").andExpect(status().isBadRequest());
        avaliar(cookieDe(ana), a, "{" + chef + "\"nota\":6}").andExpect(status().isBadRequest());
        avaliar(cookieDe(ana), a, "{" + chef + "\"nota\":5,\"tags\":[\"EDUCADO\"]}").andExpect(status().isBadRequest());
        avaliar(cookieDe(ana), a, "{" + chef + "\"nota\":5,\"tags\":[\"PONTUAL\",\"SIMPATICO\",\"COZINHA_LIMPA\","
                + "\"COMIDA_EXCELENTE\",\"APRESENTACAO_CAPRICHADA\",\"ATRASOU\"]}").andExpect(status().isBadRequest());
        avaliar(cookieDe(ana), a, "{" + chef + "\"nota\":5,\"comentario\":\"" + "x".repeat(1001) + "\"}")
                .andExpect(status().isBadRequest());
        avaliar(cookieDe(ana), a, "{\"nota\":5}").andExpect(status().isBadRequest());
        assertThat(avaliacaoRepository.count()).isZero();

        avaliar(cookieDe(ana), a, "{" + chef + "\"nota\":5}").andExpect(status().isCreated());
        avaliar(cookieDe(ana), a, "{" + chef + "\"nota\":4}").andExpect(status().isConflict());
        assertThat(chefeRepository.findById(bia.getId()).orElseThrow().getTotalAvaliacoes()).isEqualTo(1);
    }

    @Test
    void clienteNaoAvaliaChefQueSaiuNemQuemNaoParticipa() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        Agendamento a = novoAgendamento(ana, BASE, bia, caio);
        a.getChefes().removeIf(c -> c.getId().equals(caio.getId())); // Caio recusou antes da conclusão
        a = concluido(a, LocalDateTime.now().minusDays(1));

        avaliar(cookieDe(ana), a, "{\"chefeId\":\"" + caio.getId() + "\",\"nota\":1}").andExpect(status().isNotFound());
        avaliar(cookieDe(caio), a, "{\"nota\":1}").andExpect(status().isNotFound());
        avaliar(cookieDe(novoCliente("Edu")), a, "{\"chefeId\":\"" + bia.getId() + "\",\"nota\":1}")
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/agendamentos/" + a.getId() + "/avaliacoes/pendentes").cookie(cookieDe(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bia.getId().toString()))
                .andExpect(jsonPath("$[0].nome").value("Bia"))
                .andExpect(jsonPath("$[0].tipo").value("CHEFE"))
                .andExpect(jsonPath("$[0].prazo").isNotEmpty());
    }

    @Test
    void pendentesDoChefSomemDepoisDeAvaliar() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/agendamentos/" + a.getId() + "/avaliacoes/pendentes").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("CLIENTE"));
        avaliar(cookieDe(bia), a, "{\"nota\":5}").andExpect(status().isCreated());
        mockMvc.perform(get("/agendamentos/" + a.getId() + "/avaliacoes/pendentes").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void odioNaAvaliacaoBloqueiaENaoGrava() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));

        avaliar(cookieDe(ana), a, "{\"chefeId\":\"" + bia.getId() + "\",\"nota\":1,\"comentario\":\"que odiento\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Conteúdo bloqueado e conta suspensa para análise da moderação"));

        assertThat(avaliacaoRepository.count()).isZero();
        ChefeModel depois = chefeRepository.findById(bia.getId()).orElseThrow();
        assertThat(depois.getTotalAvaliacoes()).isZero();
        assertThat(depois.getSomaNotas()).isZero();
        assertThat(usuarioRepository.findById(ana.getId()).orElseThrow().getBloqueadoEm()).isNotNull();
    }

    @Test
    void ofensaEhMascaradaEOriginalFicaGuardado() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));

        avaliar(cookieDe(ana), a, "{\"chefeId\":\"" + bia.getId() + "\",\"nota\":2,\"comentario\":\"um palavrao\"}")
                .andExpect(status().isCreated());

        assertThat(unica().getComentario()).isEqualTo("um ***");
        assertThat(unica().getComentarioOriginal()).isEqualTo("um palavrao");
    }

    // ---------------------------------------------------------------------

    private ResultActions avaliar(Cookie quem, Agendamento agendamento, String corpo) throws Exception {
        return mockMvc.perform(post("/agendamentos/" + agendamento.getId() + "/avaliacoes").cookie(quem)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private Agendamento concluido(Agendamento agendamento, LocalDateTime concluidoEm) {
        agendamento.setStatus(StatusAgendamento.CONCLUIDO);
        agendamento.setConcluidoEm(concluidoEm);
        return agendamentoRepository.save(agendamento);
    }

    private Avaliacao unica() {
        List<Avaliacao> todas = avaliacaoRepository.findAll();
        assertThat(todas).hasSize(1);
        return todas.get(0);
    }

    Agendamento novoAgendamento(UserModel cliente, LocalDateTime inicio, ChefeModel... chefes) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(novoEndereco(cliente));
        agendamento.setChefes(new ArrayList<>(List.of(chefes)));
        agendamento.setDataHoraInicial(inicio);
        agendamento.setDataHoraFinal(inicio.plusHours(2));
        agendamento.setQuantidadePessoas(2);
        agendamento.setValorTotal(new BigDecimal("200.00"));
        return agendamentoRepository.save(agendamento);
    }

    Pratos novoPrato(ChefeModel chefe, String nome) {
        Pratos prato = new Pratos();
        prato.setNomePrato(nome);
        prato.setDescricao("Prato " + nome);
        prato.setImageUrl("https://img.test/" + nome + ".jpg");
        prato.setChefeModel(chefe);
        return pratosRepository.save(prato);
    }

    EnderecoModel novoEndereco(UserModel dono) {
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
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        return chefeRepository.save(chefe);
    }

    Cookie cookieDe(UserModel usuario) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(usuario.getId(), usuario.getEmail(), TipoUsuario.CLIENTE)));
    }

    Cookie cookieDe(ChefeModel chefe) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(chefe.getId(), chefe.getEmail(), TipoUsuario.CHEFE)));
    }
}
