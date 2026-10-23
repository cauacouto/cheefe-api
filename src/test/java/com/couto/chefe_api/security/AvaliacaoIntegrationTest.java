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
import com.couto.chefe_api.domain.DenunciaAvaliacao;
import com.couto.chefe_api.repository.AvaliacaoRepository;
import com.couto.chefe_api.repository.BloqueioContaRepository;
import com.couto.chefe_api.repository.NotificacaoRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Avaliar depois de um agendamento concluído (spec avaliações: "Quem avalia", "Média e anonimato"). */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.couto.chefe_api.FiltroDeTeste.class)
class AvaliacaoIntegrationTest {

    static final LocalDateTime BASE = LocalDateTime.now().minusDays(3).withHour(18).withMinute(0).withSecond(0).withNano(0);

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
    @Autowired ModeracaoRegistry moderacaoRegistry;
    @Autowired com.couto.chefe_api.repository.AcaoModeracaoRepository acaoModeracaoRepository;
    @Autowired com.couto.chefe_api.repository.DenunciaAvaliacaoRepository denunciaAvaliacaoRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void limparBanco() {
        notificacaoRepository.deleteAll();
        acaoModeracaoRepository.deleteAll();
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


    // --- Task 3: reputação e lista pública ---------------------------------

    @Test
    void listaPublicaSemIdDeAutorOrdenadaPelaPublicacao() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana Souza");
        UserModel edu = novoCliente("Edu");
        UserModel gil = novoCliente("Gil");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        LocalDateTime outubro = LocalDateTime.of(2026, 10, 5, 12, 0);
        // criada antes, publicada depois: a ordem é a da publicação
        Avaliacao comNome = avaliacaoSalva(a, ana, bia, 5, "Ótima", false, outubro.plusDays(2), "PONTUAL");
        comNome.setCriadaEm(outubro.minusDays(5));
        avaliacaoRepository.save(comNome);
        avaliacaoSalva(a, edu, bia, 4, "Boa", true, outubro.plusDays(1), "SIMPATICO", "COMIDA_EXCELENTE");
        Avaliacao removida = avaliacaoSalva(a, gil, bia, 1, "***", true, outubro);
        removida.setComentarioRemovido(true);
        avaliacaoRepository.save(removida);
        // comentário com nome ainda esperando: fora da lista
        avaliacaoSalva(a, novoCliente("Ivo"), bia, 3, "Espera", false, null);

        mockMvc.perform(get("/chefe/" + bia.getId() + "/avaliacoes").param("size", "1000").param("sort", "nota,asc")
                        .cookie(cookieDe(novoCliente("Leo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(50))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].autor").value("Ana"))
                .andExpect(jsonPath("$.content[0].comentario").value("Ótima"))
                .andExpect(jsonPath("$.content[0].quando").value("outubro de 2026"))
                .andExpect(jsonPath("$.content[0].tags[0].codigo").value("PONTUAL"))
                .andExpect(jsonPath("$.content[0].tags[0].rotulo").value("Pontual"))
                .andExpect(jsonPath("$.content[0].id").value(comNome.getId()))
                .andExpect(jsonPath("$.content[0].autorId").doesNotExist())
                .andExpect(jsonPath("$.content[0].agendamentoId").doesNotExist())
                .andExpect(jsonPath("$.content[1].autor").value("Cliente verificado"))
                .andExpect(jsonPath("$.content[1].tags.length()").value(2))
                .andExpect(jsonPath("$.content[1].tags[0].codigo").value("COMIDA_EXCELENTE"))
                .andExpect(jsonPath("$.content[2].comentario").value("Comentário removido pela moderação"))
                .andExpect(jsonPath("$.content[2].nota").value(1));
    }

    @Test
    void comentarioSobreClienteNuncaApareceEmRotaPublica() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        Avaliacao sobreAna = avaliacaoSalva(a, ana, bia, 2, "Cliente difícil", true, LocalDateTime.now());
        sobreAna.setAutorId(bia.getId());
        sobreAna.setAutorTipo(TipoUsuario.CHEFE);
        sobreAna.setAvaliadoId(ana.getId());
        sobreAna.setAvaliadoTipo(TipoUsuario.CLIENTE);
        avaliacaoRepository.save(sobreAna);

        mockMvc.perform(get("/chefe/" + ana.getId() + "/avaliacoes").cookie(cookieDe(bia)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/chefe/" + bia.getId() + "/avaliacoes").cookie(cookieDe(ana)))
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/clientes/" + ana.getId() + "/reputacao").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.comentarios").doesNotExist())
                .andExpect(jsonPath("$.totalAvaliacoes").value(0));
    }

    @Test
    void reputacaoDoChefComMediaNulaAbaixoDe5EContagemDeTags() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        int[] notas = {5, 5, 4, 4, 4};
        for (int i = 0; i < notas.length; i++) {
            avaliacaoSalva(a, i == 0 ? ana : novoCliente("C" + i), bia, notas[i], null, true, LocalDateTime.now(),
                    i < 3 ? new String[]{"PONTUAL", "SIMPATICO"} : new String[]{"PONTUAL"});
        }
        bia.setSomaNotas(18);
        bia.setTotalAvaliacoes(4);
        chefeRepository.save(bia);
        Cookie leo = cookieDe(novoCliente("Leo"));

        mockMvc.perform(get("/chefe/" + bia.getId() + "/reputacao").cookie(leo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notaMedia").doesNotExist())
                .andExpect(jsonPath("$.totalAvaliacoes").value(4));

        bia.setSomaNotas(22);
        bia.setTotalAvaliacoes(5);
        chefeRepository.save(bia);
        mockMvc.perform(get("/chefe/" + bia.getId() + "/reputacao").cookie(leo))
                .andExpect(jsonPath("$.notaMedia").value(4.4))
                .andExpect(jsonPath("$.totalAvaliacoes").value(5))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.tags[0].codigo").value("PONTUAL"))
                .andExpect(jsonPath("$.tags[0].rotulo").value("Pontual"))
                .andExpect(jsonPath("$.tags[0].total").value(5))
                .andExpect(jsonPath("$.tags[1].codigo").value("SIMPATICO"))
                .andExpect(jsonPath("$.tags[1].total").value(3));
        mockMvc.perform(get("/chefe/" + UUID.randomUUID() + "/reputacao").cookie(leo))
                .andExpect(status().isNotFound());
    }

    @Test
    void reputacaoDoClienteSoParaChefComAgendamentoComEle() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        UserModel ana = novoCliente("Ana");
        ana.setSomaNotas(23);
        ana.setTotalAvaliacoes(5);
        usuarioRepository.save(ana);
        novoAgendamento(ana, BASE, bia);

        mockMvc.perform(get("/clientes/" + ana.getId() + "/reputacao").cookie(cookieDe(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notaMedia").value(4.6))
                .andExpect(jsonPath("$.totalAvaliacoes").value(5));
        mockMvc.perform(get("/clientes/" + ana.getId() + "/reputacao").cookie(cookieDe(caio)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/clientes/" + ana.getId() + "/reputacao").cookie(cookieDe(novoCliente("Edu"))))
                .andExpect(status().isForbidden());
    }


    // --- Task 4: resposta do chef, denúncia e moderação ----------------------

    @Test
    void chefRespondeEditaEOClienteENotificadoUmaVez() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        Avaliacao av = avaliacaoSalva(a, ana, bia, 4, "Muito boa", true, LocalDateTime.now());

        responder(cookieDe(bia), av, "Obrigada!").andExpect(status().isOk());
        responder(cookieDe(bia), av, "Obrigada, volte sempre um palavrao").andExpect(status().isOk());

        Avaliacao depois = avaliacaoRepository.findById(av.getId()).orElseThrow();
        assertThat(depois.getResposta()).isEqualTo("Obrigada, volte sempre um ***");
        assertThat(depois.getRespostaOriginal()).isEqualTo("Obrigada, volte sempre um palavrao");
        assertThat(depois.getRespondidaEm()).isNotNull();
        mockMvc.perform(get("/chefe/" + bia.getId() + "/avaliacoes").cookie(cookieDe(ana)))
                .andExpect(jsonPath("$.content[0].resposta").value("Obrigada, volte sempre um ***"));
        assertThat(notificacaoRepository.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getDestinatarioId()).isEqualTo(ana.getId());
            assertThat(n.getEvento().name()).isEqualTo("AVALIACAO_RESPONDIDA");
            assertThat(n.getMensagem()).isEqualTo("Chef Bia respondeu sua avaliação");
            assertThat(n.getReferenciaId()).isEqualTo(String.valueOf(av.getId()));
        });
    }

    @Test
    void respostaSoDoChefAvaliadoESoEmComentarioPublicado() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        Avaliacao publicada = avaliacaoSalva(a, ana, bia, 4, "Boa", true, LocalDateTime.now());
        Avaliacao semComentario = avaliacaoSalva(a, novoCliente("Edu"), bia, 4, null, true, LocalDateTime.now());
        Avaliacao esperando = avaliacaoSalva(a, novoCliente("Gil"), bia, 4, "Com nome", false, null);
        Avaliacao removida = avaliacaoSalva(a, novoCliente("Ivo"), bia, 1, "Ruim", true, LocalDateTime.now());
        removida.setComentarioRemovido(true);
        avaliacaoRepository.save(removida);

        responder(cookieDe(novoChefe("Caio")), publicada, "Oi").andExpect(status().isNotFound());
        responder(cookieDe(ana), publicada, "Oi").andExpect(status().isForbidden()); // papel errado
        responder(cookieDe(bia), semComentario, "Oi").andExpect(status().isConflict());
        responder(cookieDe(bia), esperando, "Oi").andExpect(status().isConflict());
        responder(cookieDe(bia), removida, "Oi").andExpect(status().isConflict());
        responder(cookieDe(bia), publicada, " ").andExpect(status().isBadRequest());
        responder(cookieDe(bia), publicada, "x".repeat(1001)).andExpect(status().isBadRequest());
        assertThat(avaliacaoRepository.findAll()).allSatisfy(av -> assertThat(av.getResposta()).isNull());
    }

    @Test
    void respostaComOdioBloqueiaOChefENaoGrava() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        Avaliacao av = avaliacaoSalva(a, ana, bia, 4, "Boa", true, LocalDateTime.now());

        responder(cookieDe(bia), av, "seu odiento").andExpect(status().isForbidden());

        assertThat(avaliacaoRepository.findById(av.getId()).orElseThrow().getResposta()).isNull();
        assertThat(chefeRepository.findById(bia.getId()).orElseThrow().getBloqueadoEm()).isNotNull();
    }

    @Test
    void chefDenunciaUmaVezEAModeracaoENotificada() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        Avaliacao av = avaliacaoSalva(a, ana, bia, 1, "Chef horrível ***", true, LocalDateTime.now());
        av.setComentarioOriginal("Chef horrível palavrao");
        avaliacaoRepository.save(av);

        denunciar(cookieDe(bia), av, "{\"motivo\":\"OFENSIVA\",\"descricao\":\"  mentira  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("ABERTA"));
        denunciar(cookieDe(bia), av, "{\"motivo\":\"FALSA\"}").andExpect(status().isConflict());
        denunciar(cookieDe(novoChefe("Caio")), av, "{\"motivo\":\"FALSA\"}").andExpect(status().isNotFound());
        denunciar(cookieDe(bia), av, "{\"motivo\":\"INVENTADO\"}").andExpect(status().isBadRequest());

        assertThat(notificacaoRepository.findAll())
                .filteredOn(n -> n.getEvento().name().equals("AVALIACAO_DENUNCIADA"))
                .hasSize(moderacaoRegistry.ids().size())
                .allSatisfy(n -> assertThat(n.getMensagem()).isEqualTo("Nova denúncia de avaliação (OFENSIVA)"));
        mockMvc.perform(get("/moderacao/denuncias-avaliacoes").cookie(cookieModerador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].motivo").value("OFENSIVA"))
                .andExpect(jsonPath("$.content[0].descricao").value("mentira"))
                .andExpect(jsonPath("$.content[0].textoDenunciado").value("Chef horrível ***"))
                .andExpect(jsonPath("$.content[0].comentarioOriginal").value("Chef horrível palavrao"))
                .andExpect(jsonPath("$.content[0].autor.nome").value("Ana"))
                .andExpect(jsonPath("$.content[0].denunciante.nome").value("Bia"));
    }

    @Test
    void removerComentarioMantemNotaEEscondeONome() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        Avaliacao av = avaliacaoSalva(a, ana, bia, 1, "Comentário ruim", false, LocalDateTime.now(), "ATRASOU");
        bia.setSomaNotas(1);
        bia.setTotalAvaliacoes(1);
        chefeRepository.save(bia);
        Long primeira = denunciaCriada(cookieDe(bia), av, "OFENSIVA");
        DenunciaAvaliacao outra = new DenunciaAvaliacao();
        outra.setAvaliacao(av);
        outra.setDenuncianteId(UUID.randomUUID());
        outra.setDenuncianteTipo(TipoUsuario.CHEFE);
        outra.setAutorId(ana.getId());
        outra.setAutorTipo(TipoUsuario.CLIENTE);
        outra.setTextoDenunciado("Comentário ruim");
        outra.setMotivo(com.couto.chefe_api.enums.MotivoDenunciaAvaliacao.SPAM);
        denunciaAvaliacaoRepository.save(outra);

        mockMvc.perform(patch("/moderacao/denuncias-avaliacoes/" + primeira + "/remover-comentario").cookie(cookieModerador()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/chefe/" + bia.getId() + "/avaliacoes").cookie(cookieDe(ana)))
                .andExpect(jsonPath("$.content[0].comentario").value("Comentário removido pela moderação"))
                .andExpect(jsonPath("$.content[0].autor").value("Cliente verificado"))
                .andExpect(jsonPath("$.content[0].nota").value(1))
                .andExpect(jsonPath("$.content[0].tags[0].codigo").value("ATRASOU"));
        mockMvc.perform(get("/chefe/" + bia.getId() + "/reputacao").cookie(cookieDe(ana)))
                .andExpect(jsonPath("$.totalAvaliacoes").value(1));
        assertThat(denunciaAvaliacaoRepository.findAll()).allSatisfy(d -> assertThat(d.getStatus().name()).isEqualTo("PROCEDENTE"));
        assertThat(acaoModeracaoRepository.findAll()).singleElement().satisfies(acao -> {
            assertThat(acao.getAcao().name()).isEqualTo("REMOVER_COMENTARIO_AVALIACAO");
            assertThat(acao.getDenunciaId()).isEqualTo(primeira);
            assertThat(acao.getChefeId()).isEqualTo(bia.getId());
        });
        responder(cookieDe(bia), av, "Resposta").andExpect(status().isConflict());
        mockMvc.perform(patch("/moderacao/denuncias-avaliacoes/" + primeira + "/arquivar").cookie(cookieModerador()))
                .andExpect(status().isConflict());
    }

    @Test
    void arquivarDenunciaDeixaOComentarioERegistra() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        Avaliacao av = avaliacaoSalva(a, ana, bia, 3, "Mediano", true, LocalDateTime.now());
        Long denuncia = denunciaCriada(cookieDe(bia), av, "FALSA");

        mockMvc.perform(patch("/moderacao/denuncias-avaliacoes/" + denuncia + "/arquivar").cookie(cookieModerador()))
                .andExpect(status().isNoContent());

        assertThat(denunciaAvaliacaoRepository.findById(denuncia).orElseThrow().getStatus().name()).isEqualTo("ARQUIVADA");
        assertThat(avaliacaoRepository.findById(av.getId()).orElseThrow().isComentarioRemovido()).isFalse();
        assertThat(acaoModeracaoRepository.findAll()).singleElement()
                .satisfies(acao -> assertThat(acao.getAcao().name()).isEqualTo("ARQUIVAR_DENUNCIA_AVALIACAO"));
        mockMvc.perform(get("/moderacao/denuncias-avaliacoes").param("status", "ARQUIVADA").cookie(cookieModerador()))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void comentariosSobreClientesSoNaModeracao() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        Avaliacao sobreAna = avaliacaoSalva(a, ana, bia, 2, "Cliente grosseiro ***", true, LocalDateTime.now());
        sobreAna.setComentarioOriginal("Cliente grosseiro palavrao");
        sobreAna.setAutorId(bia.getId());
        sobreAna.setAutorTipo(TipoUsuario.CHEFE);
        sobreAna.setAvaliadoId(ana.getId());
        sobreAna.setAvaliadoTipo(TipoUsuario.CLIENTE);
        avaliacaoRepository.save(sobreAna);
        avaliacaoSalva(a, ana, bia, 5, "Sobre o chef", true, LocalDateTime.now());

        mockMvc.perform(get("/moderacao/comentarios-de-clientes").cookie(cookieModerador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].comentario").value("Cliente grosseiro ***"))
                .andExpect(jsonPath("$.content[0].comentarioOriginal").value("Cliente grosseiro palavrao"))
                .andExpect(jsonPath("$.content[0].nota").value(2))
                .andExpect(jsonPath("$.content[0].autor.nome").value("Bia"))
                .andExpect(jsonPath("$.content[0].avaliado.nome").value("Ana"));
        for (Cookie quem : List.of(cookieDe(ana), cookieDe(bia))) {
            mockMvc.perform(get("/moderacao/comentarios-de-clientes").cookie(quem)).andExpect(status().isNotFound());
            mockMvc.perform(get("/moderacao/denuncias-avaliacoes").cookie(quem)).andExpect(status().isNotFound());
        }
    }

    @Test
    void chefBloqueadoNaoTemListaNemReputacaoPublica() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        bia.setBloqueadoEm(LocalDateTime.now());
        chefeRepository.save(bia);
        Cookie ana = cookieDe(novoCliente("Ana"));

        mockMvc.perform(get("/chefe/" + bia.getId() + "/avaliacoes").cookie(ana)).andExpect(status().isNotFound());
        mockMvc.perform(get("/chefe/" + bia.getId() + "/reputacao").cookie(ana)).andExpect(status().isNotFound());
    }

    @Test
    void chefInativoNaoAvaliaNaoRespondeNemDenuncia() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        Avaliacao av = avaliacaoSalva(a, ana, bia, 4, "Boa", true, LocalDateTime.now());
        bia.setAtivo(false);
        chefeRepository.save(bia);

        avaliar(cookieDe(bia), a, "{\"nota\":5}").andExpect(status().isForbidden());
        responder(cookieDe(bia), av, "Oi").andExpect(status().isForbidden());
        denunciar(cookieDe(bia), av, "{\"motivo\":\"FALSA\"}").andExpect(status().isForbidden());

        assertThat(avaliacaoRepository.findAll()).singleElement()
                .satisfies(x -> assertThat(x.getResposta()).isNull());
        assertThat(denunciaAvaliacaoRepository.findAll()).isEmpty();
    }

    @Test
    void denunciaSoDeComentarioPublicadoEPapelCerto() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        Avaliacao semComentario = avaliacaoSalva(a, ana, bia, 4, null, true, LocalDateTime.now());
        Avaliacao esperando = avaliacaoSalva(a, novoCliente("Gil"), bia, 4, "Com nome", false, null);
        Avaliacao removida = avaliacaoSalva(a, novoCliente("Ivo"), bia, 1, "Ruim", true, LocalDateTime.now());
        removida.setComentarioRemovido(true);
        avaliacaoRepository.save(removida);

        for (Avaliacao av : List.of(semComentario, esperando, removida)) {
            denunciar(cookieDe(bia), av, "{\"motivo\":\"FALSA\"}").andExpect(status().isConflict());
        }
        denunciar(cookieDe(ana), removida, "{\"motivo\":\"FALSA\"}").andExpect(status().isForbidden());
        denunciar(cookieModerador(), removida, "{\"motivo\":\"FALSA\"}").andExpect(status().isForbidden());
        responder(cookieModerador(), removida, "Oi").andExpect(status().isForbidden());
        mockMvc.perform(patch("/moderacao/denuncias-avaliacoes/999999/arquivar").cookie(cookieModerador()))
                .andExpect(status().isNotFound());
        assertThat(denunciaAvaliacaoRepository.findAll()).isEmpty();
    }

    @Test
    void edicaoComOdioMantemARespostaAnterior() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Agendamento a = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        Avaliacao av = avaliacaoSalva(a, ana, bia, 4, "Boa", true, LocalDateTime.now());
        responder(cookieDe(bia), av, "Obrigada").andExpect(status().isOk());

        responder(cookieDe(bia), av, "vou te pegar").andExpect(status().isForbidden());

        assertThat(avaliacaoRepository.findById(av.getId()).orElseThrow().getResposta()).isEqualTo("Obrigada");
    }

    // --- Task 5: conta apagada ------------------------------------------------

    @Test
    void contaApagadaDescontaOsContadoresDoChef() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Agendamento daAna = concluido(novoAgendamento(ana, BASE, bia), LocalDateTime.now().minusDays(1));
        Avaliacao daAnaSobreBia = avaliacaoSalva(daAna, ana, bia, 5, "Ótima", true, LocalDateTime.now());
        int[] outras = {4, 4, 4, 5};
        for (int nota : outras) {
            UserModel outro = novoCliente("C" + nota);
            avaliacaoSalva(concluido(novoAgendamento(outro, BASE, bia), LocalDateTime.now().minusDays(1)),
                    outro, bia, nota, null, true, LocalDateTime.now());
        }
        bia.setSomaNotas(22);
        bia.setTotalAvaliacoes(5);
        chefeRepository.save(bia);
        // a avaliação do chef sobre a Ana vai junto, e não mexe nos contadores do chef
        Avaliacao sobreAna = new Avaliacao();
        sobreAna.setAgendamento(daAna);
        sobreAna.setAutorId(bia.getId());
        sobreAna.setAutorTipo(TipoUsuario.CHEFE);
        sobreAna.setAvaliadoId(ana.getId());
        sobreAna.setAvaliadoTipo(TipoUsuario.CLIENTE);
        sobreAna.setNota(1);
        sobreAna.setComentario("Cliente difícil");
        sobreAna.setPublicadoEm(LocalDateTime.now());
        avaliacaoRepository.save(sobreAna);
        denunciaCriada(cookieDe(bia), daAnaSobreBia, "FALSA");
        mockMvc.perform(get("/chefe/" + bia.getId() + "/reputacao").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.notaMedia").value(4.4));
        assertThat(notificacaoRepository.findAll())
                .anyMatch(n -> n.getEvento().name().equals("AVALIACAO_DENUNCIADA"));

        mockMvc.perform(delete("/usuario/me").cookie(cookieDe(ana))).andExpect(status().is2xxSuccessful());

        ChefeModel depois = chefeRepository.findById(bia.getId()).orElseThrow();
        assertThat(depois.getTotalAvaliacoes()).isEqualTo(4);
        assertThat(depois.getSomaNotas()).isEqualTo(17);
        mockMvc.perform(get("/chefe/" + bia.getId() + "/reputacao").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.notaMedia").doesNotExist())
                .andExpect(jsonPath("$.totalAvaliacoes").value(4));
        mockMvc.perform(get("/clientes/" + ana.getId() + "/reputacao").cookie(cookieDe(bia)))
                .andExpect(status().isNotFound());
        assertThat(avaliacaoRepository.findAll()).hasSize(4).noneMatch(a -> a.getAutorId().equals(ana.getId())
                || a.getAvaliadoId().equals(ana.getId()));
        assertThat(denunciaAvaliacaoRepository.findAll()).isEmpty();
        // a fila da moderação não fica apontando para uma denúncia que não existe mais
        assertThat(notificacaoRepository.findAll())
                .noneMatch(n -> n.getEvento().name().equals("AVALIACAO_DENUNCIADA"));
    }

    // ---------------------------------------------------------------------

    private ResultActions avaliar(Cookie quem, Agendamento agendamento, String corpo) throws Exception {
        return mockMvc.perform(post("/agendamentos/" + agendamento.getId() + "/avaliacoes").cookie(quem)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private ResultActions responder(Cookie quem, Avaliacao avaliacao, String texto) throws Exception {
        return mockMvc.perform(put("/avaliacoes/" + avaliacao.getId() + "/resposta").cookie(quem)
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"" + texto + "\"}"));
    }

    private ResultActions denunciar(Cookie quem, Avaliacao avaliacao, String corpo) throws Exception {
        return mockMvc.perform(post("/avaliacoes/" + avaliacao.getId() + "/denuncias").cookie(quem)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private Long denunciaCriada(Cookie quem, Avaliacao avaliacao, String motivo) throws Exception {
        denunciar(quem, avaliacao, "{\"motivo\":\"" + motivo + "\"}").andExpect(status().isCreated());
        return denunciaAvaliacaoRepository.findAll().stream()
                .filter(d -> d.getAvaliacao().getId().equals(avaliacao.getId()))
                .findFirst().orElseThrow().getId();
    }

    private Cookie cookieModerador() {
        String email = "moderacao@chefhome.test"; // application-test.yml
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(moderacaoRegistry.idDe(email), email, TipoUsuario.MODERADOR)));
    }

    private Agendamento concluido(Agendamento agendamento, LocalDateTime concluidoEm) {
        agendamento.setStatus(StatusAgendamento.CONCLUIDO);
        agendamento.setConcluidoEm(concluidoEm);
        return agendamentoRepository.save(agendamento);
    }

    /** Cliente avaliando chef, gravada direto (o fluxo de criação já tem testes acima). */
    private Avaliacao avaliacaoSalva(Agendamento agendamento, UserModel autor, ChefeModel chef, int nota,
                                     String comentario, boolean anonimo, LocalDateTime publicadoEm, String... tags) {
        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setAgendamento(agendamento);
        avaliacao.setAutorId(autor.getId());
        avaliacao.setAutorTipo(TipoUsuario.CLIENTE);
        avaliacao.setAvaliadoId(chef.getId());
        avaliacao.setAvaliadoTipo(TipoUsuario.CHEFE);
        avaliacao.setNota(nota);
        avaliacao.setComentario(comentario);
        avaliacao.setAnonimo(anonimo);
        avaliacao.setPublicadoEm(publicadoEm);
        avaliacao.setTags(new java.util.HashSet<>(List.of(tags)));
        return avaliacaoRepository.save(avaliacao);
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
