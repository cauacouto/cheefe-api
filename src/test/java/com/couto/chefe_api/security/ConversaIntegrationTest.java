package com.couto.chefe_api.security;

import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.service.NotificacaoService;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.Denuncia;
import com.couto.chefe_api.enums.MotivoDenuncia;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.repository.AcaoModeracaoRepository;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.DenunciaRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.ConversaRepository;
import com.couto.chefe_api.repository.DenunciaMensagemRepository;
import com.couto.chefe_api.repository.MensagemRepository;
import com.couto.chefe_api.repository.NotificacaoRepository;
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
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.couto.chefe_api.FiltroDeTeste.class)
class ConversaIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired ConversaRepository conversaRepository;
    @Autowired MensagemRepository mensagemRepository;
    @Autowired DenunciaMensagemRepository denunciaMensagemRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired AcaoModeracaoRepository acaoModeracaoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired DenunciaRepository denunciaRepository;
    @Autowired NotificacaoService notificacaoService;
    @Autowired ModeracaoRegistry moderacaoRegistry;
    @Autowired com.couto.chefe_api.repository.BloqueioContaRepository bloqueioContaRepository;

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
        acaoModeracaoRepository.deleteAll();
        bloqueioContaRepository.deleteAll();
        denunciaMensagemRepository.deleteAll();
        mensagemRepository.deleteAll();
        conversaRepository.deleteAll();
        notificacaoRepository.deleteAll();
        denunciaRepository.deleteAll();
        agendamentoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void abrirConversaEhIdempotenteEChefNaoAbre() throws Exception {
        long id = abrir(ana, bia).andExpect(status().isCreated()).andReturnId();
        long deNovo = abrir(ana, bia).andExpect(status().isOk()).andReturnId();

        assertThat(deNovo).isEqualTo(id);
        mockMvc.perform(post("/conversas").cookie(cookie(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chefeId\":\"" + bia.getId() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void trocaDeMensagensComNaoLidasPreviaEVisto() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        enviar(ana, conversa, "  Oi, você faz moqueca?  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.texto").value("Oi, você faz moqueca?"))
                .andExpect(jsonPath("$.minha").value(true));

        mockMvc.perform(get("/conversas").cookie(cookie(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].naoLidas").value(1))
                .andExpect(jsonPath("$.content[0].ultimaMensagem.texto").value("Oi, você faz moqueca?"))
                .andExpect(jsonPath("$.content[0].outroParticipante.nome").value("Ana"));

        mockMvc.perform(patch("/conversas/" + conversa + "/lidas").cookie(cookie(bia))).andExpect(status().isNoContent());

        mockMvc.perform(get("/conversas").cookie(cookie(bia))).andExpect(jsonPath("$.content[0].naoLidas").value(0));
        mockMvc.perform(get("/conversas/" + conversa + "/mensagens").cookie(cookie(ana)))
                .andExpect(jsonPath("$[0].lida").value(true))
                .andExpect(jsonPath("$[0].lidaEm").isNotEmpty());
    }

    @Test
    void dezMensagensUmaNotificacaoEDepoisDeLerNotificaDeNovo() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        for (int i = 0; i < 10; i++) {
            enviar(ana, conversa, "mensagem " + i).andExpect(status().isCreated());
        }
        mockMvc.perform(get("/notificacoes/nao-lidas/contagem").cookie(cookie(bia))).andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/notificacoes").cookie(cookie(bia)))
                .andExpect(jsonPath("$.content[0].evento").value("MENSAGEM_RECEBIDA"))
                .andExpect(jsonPath("$.content[0].mensagem").value("Nova mensagem de Ana"))
                .andExpect(jsonPath("$.content[0].referenciaId").value(String.valueOf(conversa)));

        mockMvc.perform(patch("/conversas/" + conversa + "/lidas").cookie(cookie(bia))).andExpect(status().isNoContent());
        mockMvc.perform(get("/notificacoes/nao-lidas/contagem").cookie(cookie(bia))).andExpect(jsonPath("$.total").value(0));

        enviar(ana, conversa, "mais uma").andExpect(status().isCreated());
        mockMvc.perform(get("/notificacoes/nao-lidas/contagem").cookie(cookie(bia))).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void paginacaoComAntesDe() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        for (int i = 1; i <= 5; i++) {
            enviar(ana, conversa, "m" + i);
        }
        String pagina1 = mockMvc.perform(get("/conversas/" + conversa + "/mensagens").param("size", "2").cookie(cookie(ana)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].texto").value("m5"))
                .andReturn().getResponse().getContentAsString();
        long ultimoId = JSON.readTree(pagina1).get(1).get("id").asLong();

        mockMvc.perform(get("/conversas/" + conversa + "/mensagens").param("size", "2").param("antesDe", String.valueOf(ultimoId))
                        .cookie(cookie(ana)))
                .andExpect(jsonPath("$[0].texto").value("m3"))
                .andExpect(jsonPath("$[1].texto").value("m2"));
    }

    @Test
    void terceiroNaoVeNemMexeNaConversa() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        UserModel intruso = novoCliente("Intruso");

        mockMvc.perform(get("/conversas/" + conversa + "/mensagens").cookie(cookie(intruso))).andExpect(status().isNotFound());
        enviar(intruso, conversa, "oi").andExpect(status().isNotFound());
        mockMvc.perform(patch("/conversas/" + conversa + "/lidas").cookie(cookie(intruso))).andExpect(status().isNotFound());
        mockMvc.perform(get("/conversas").cookie(cookie(intruso))).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void textoInvalidoDa400() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();

        enviar(ana, conversa, "   ").andExpect(status().isBadRequest());
        enviar(ana, conversa, "x".repeat(2001)).andExpect(status().isBadRequest());
    }

    @Test
    void chefApagadoBloqueiaEnvioNosDoisSentidos() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        bia.setAtivo(false);
        chefeRepository.save(bia);

        enviar(ana, conversa, "oi").andExpect(status().isForbidden());
        mockMvc.perform(get("/conversas/" + conversa + "/mensagens").cookie(cookie(ana))).andExpect(status().isOk());
    }

    @Test
    void editarEApagarPelaApi() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        long mensagem = new Resposta(enviar(ana, conversa, "oi")).andReturnId();

        mockMvc.perform(patch("/conversas/" + conversa + "/mensagens/" + mensagem).cookie(cookie(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"olá\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("olá"))
                .andExpect(jsonPath("$.editada").value(true));
        mockMvc.perform(patch("/conversas/" + conversa + "/mensagens/" + mensagem).cookie(cookie(bia))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"invasão\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/conversas/" + conversa + "/mensagens/" + mensagem).cookie(cookie(bia)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/conversas/" + conversa + "/mensagens/" + mensagem).cookie(cookie(ana)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/conversas/" + conversa + "/mensagens/" + mensagem).cookie(cookie(ana)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/conversas/" + conversa + "/mensagens").cookie(cookie(bia)))
                .andExpect(jsonPath("$[0].apagada").value(true))
                .andExpect(jsonPath("$[0].texto").doesNotExist());
        mockMvc.perform(get("/conversas").cookie(cookie(bia)))
                .andExpect(jsonPath("$.content[0].ultimaMensagem.apagada").value(true))
                .andExpect(jsonPath("$.content[0].ultimaMensagem.texto").doesNotExist());
        mockMvc.perform(patch("/conversas/" + conversa + "/mensagens/" + mensagem).cookie(cookie(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"volta\"}"))
                .andExpect(status().isConflict());
        assertThat(mensagemRepository.findById(mensagem).orElseThrow().getTexto()).isEqualTo("olá");
    }

    @Test
    void textoOriginalSobreviveAEdicaoEExclusao() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        long mensagem = new Resposta(enviar(bia, conversa, "texto ruim")).andReturnId();
        String base = "/conversas/" + conversa + "/mensagens/" + mensagem;

        denunciar(bia, base, "OFENSIVA").andExpect(status().isNotFound());
        long denuncia = new Resposta(denunciar(ana, base, "OFENSIVA")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTA")).andReturnId();
        denunciar(ana, base, "SPAM").andExpect(status().isConflict());

        mockMvc.perform(patch(base).cookie(cookie(bia)).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"texto bom\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete(base).cookie(cookie(bia))).andExpect(status().isNoContent());

        mockMvc.perform(get("/moderacao/denuncias-mensagens").cookie(cookieModerador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(denuncia))
                .andExpect(jsonPath("$.content[0].textoDenunciado").value("texto ruim"))
                .andExpect(jsonPath("$.content[0].motivo").value("OFENSIVA"))
                .andExpect(jsonPath("$.content[0].autor.nome").value("Bia"))
                .andExpect(jsonPath("$.content[0].denunciante.nome").value("Ana"));
        mockMvc.perform(get("/notificacoes").cookie(cookieModerador()))
                .andExpect(jsonPath("$.content[0].evento").value("MENSAGEM_DENUNCIADA"))
                .andExpect(jsonPath("$.content[0].mensagem").value("Nova denúncia de mensagem (OFENSIVA)"));

        mockMvc.perform(patch("/moderacao/denuncias-mensagens/" + denuncia + "/arquivar").cookie(cookieModerador()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/moderacao/denuncias-mensagens").cookie(cookieModerador()))
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/moderacao/acoes").cookie(cookieModerador()))
                .andExpect(jsonPath("$.content[0].acao").value("ARQUIVAR_DENUNCIA_MENSAGEM"))
                .andExpect(jsonPath("$.content[0].denunciaId").value(denuncia));
    }

    // --- filtro de conteúdo ---------------------------------------------------

    @Test
    void palavraoChegaMascaradoEOOriginalFicaSoParaAModeracao() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        long mensagem = new Resposta(enviar(bia, conversa, "que palavrao de pedido")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.texto").value("que *** de pedido")).andReturnId();
        long limpa = new Resposta(enviar(bia, conversa, "tudo certo")).andReturnId();

        mockMvc.perform(get("/conversas/" + conversa + "/mensagens").cookie(cookie(ana)))
                .andExpect(jsonPath("$[1].texto").value("que *** de pedido"))
                .andExpect(jsonPath("$[1].textoOriginal").doesNotExist());
        assertThat(mensagemRepository.findById(mensagem).orElseThrow().getTextoOriginal()).isEqualTo("que palavrao de pedido");
        assertThat(mensagemRepository.findById(limpa).orElseThrow().getTextoOriginal()).isNull();

        String base = "/conversas/" + conversa + "/mensagens/" + mensagem;
        denunciar(ana, base, "OFENSIVA").andExpect(status().isCreated());
        // editar depois da denúncia não muda a cópia que a moderação vê
        mockMvc.perform(patch(base).cookie(cookie(bia)).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"desculpa\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/moderacao/denuncias-mensagens").cookie(cookieModerador()))
                .andExpect(jsonPath("$.content[0].textoDenunciado").value("que *** de pedido"))
                .andExpect(jsonPath("$.content[0].textoOriginal").value("que palavrao de pedido"));
    }

    @Test
    void odioNoChatBloqueiaAContaENadaChegaAoOutroLado() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();

        enviar(ana, conversa, "seu odiento").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Conteúdo bloqueado e conta suspensa para análise da moderação"));

        assertThat(mensagemRepository.findAll()).isEmpty();
        assertThat(usuarioRepository.findById(ana.getId()).orElseThrow().getBloqueadoEm()).isNotNull();
        assertThat(notificacaoRepository.findAll()).noneMatch(n -> n.getEvento().name().equals("MENSAGEM_RECEBIDA"));
        assertThat(bloqueioContaRepository.findAll()).singleElement()
                .satisfies(b -> assertThat(b.getTexto()).isEqualTo("seu odiento"));
    }

    @Test
    void edicaoPassaPeloFiltroEComAmeacaMantemOTextoAnterior() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        long mensagem = new Resposta(enviar(bia, conversa, "oi")).andReturnId();
        String base = "/conversas/" + conversa + "/mensagens/" + mensagem;

        mockMvc.perform(patch(base).cookie(cookie(bia)).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi palavrao\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("oi ***"));
        assertThat(mensagemRepository.findById(mensagem).orElseThrow().getTextoOriginal()).isEqualTo("oi palavrao");

        mockMvc.perform(patch(base).cookie(cookie(bia)).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"vou te pegar\"}"))
                .andExpect(status().isForbidden());

        assertThat(mensagemRepository.findById(mensagem).orElseThrow().getTexto()).isEqualTo("oi ***");
        assertThat(chefeRepository.findById(bia.getId()).orElseThrow().getBloqueadoEm()).isNotNull();
    }

    @Test
    void edicaoLimpaApagaOOriginalGuardado() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        long mensagem = new Resposta(enviar(bia, conversa, "oi palavrao")).andReturnId();

        mockMvc.perform(patch("/conversas/" + conversa + "/mensagens/" + mensagem).cookie(cookie(bia))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                .andExpect(status().isOk());

        assertThat(mensagemRepository.findById(mensagem).orElseThrow().getTextoOriginal()).isNull();
    }

    @Test
    void quemNaoParticipaRecebe404AntesDoFiltro() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        UserModel intruso = novoCliente("Edu");

        enviar(intruso, conversa, "seu odiento").andExpect(status().isNotFound());

        assertThat(usuarioRepository.findById(intruso.getId()).orElseThrow().getBloqueadoEm()).isNull();
        assertThat(bloqueioContaRepository.findAll()).isEmpty();
    }

    @Test
    void moderacaoRemoveMensagemParaOsDois() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        long mensagem = new Resposta(enviar(ana, conversa, "golpe pix")).andReturnId();
        long denuncia = new Resposta(denunciar(bia, "/conversas/" + conversa + "/mensagens/" + mensagem, "GOLPE")).andReturnId();

        mockMvc.perform(patch("/moderacao/denuncias-mensagens/" + denuncia + "/remover-mensagem").cookie(cookieModerador()))
                .andExpect(status().isNoContent());

        for (Cookie quem : new Cookie[]{cookie(ana), cookie(bia)}) {
            mockMvc.perform(get("/conversas/" + conversa + "/mensagens").cookie(quem))
                    .andExpect(jsonPath("$[0].apagada").value(true))
                    .andExpect(jsonPath("$[0].texto").doesNotExist());
        }
        assertThat(denunciaMensagemRepository.findById(denuncia).orElseThrow().getStatus().name()).isEqualTo("PROCEDENTE");
        mockMvc.perform(get("/moderacao/acoes").cookie(cookieModerador()))
                .andExpect(jsonPath("$.content[0].acao").value("REMOVER_MENSAGEM"));
    }

    @Test
    void idDeMensagemDeOutraConversaDa404MesmoParticipandoDasDuas() throws Exception {
        ChefeModel caio = novoChefe("Caio");
        long comBia = abrir(ana, bia).andReturnId();
        long comCaio = abrir(ana, caio).andReturnId();
        long minhaComCaio = new Resposta(enviar(ana, comCaio, "oi Caio")).andReturnId();
        long doCaio = new Resposta(enviar(caio, comCaio, "oi Ana")).andReturnId();
        String errada = "/conversas/" + comBia + "/mensagens/";

        mockMvc.perform(patch(errada + minhaComCaio).cookie(cookie(ana)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"x\"}")).andExpect(status().isNotFound());
        mockMvc.perform(delete(errada + minhaComCaio).cookie(cookie(ana))).andExpect(status().isNotFound());
        denunciar(ana, errada + doCaio, "SPAM").andExpect(status().isNotFound());

        assertThat(mensagemRepository.findById(minhaComCaio).orElseThrow().isApagada()).isFalse();
        assertThat(denunciaMensagemRepository.count()).isZero();
    }

    @Test
    void chefBanidoNaoMexeMaisNoChat() throws Exception {
        long conversa = abrir(ana, bia).andReturnId();
        long daBia = new Resposta(enviar(bia, conversa, "oi")).andReturnId();
        long daAna = new Resposta(enviar(ana, conversa, "oi")).andReturnId();
        bia.setAtivo(false);
        chefeRepository.save(bia);
        String base = "/conversas/" + conversa + "/mensagens/";

        mockMvc.perform(patch(base + daBia).cookie(cookie(bia)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(delete(base + daBia).cookie(cookie(bia))).andExpect(status().isForbidden());
        denunciar(bia, base + daAna, "SPAM").andExpect(status().isForbidden());
        mockMvc.perform(patch("/conversas/" + conversa + "/lidas").cookie(cookie(bia))).andExpect(status().isForbidden());
        mockMvc.perform(get(base.substring(0, base.length() - 1)).cookie(cookie(bia))).andExpect(status().isOk());
    }

    @Test
    void listaDeConversasEhPaginada() throws Exception {
        abrir(ana, bia);
        abrir(ana, novoChefe("Caio"));

        mockMvc.perform(get("/conversas").param("size", "1").cookie(cookie(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.size").value(1));
        mockMvc.perform(get("/conversas").param("sort", "cliente.email").cookie(cookie(ana)))
                .andExpect(status().isOk());
    }

    @Test
    void clienteEChefNaoVeemAFilaDeMensagens() throws Exception {
        mockMvc.perform(get("/moderacao/denuncias-mensagens").cookie(cookie(ana))).andExpect(status().isNotFound());
        mockMvc.perform(get("/moderacao/denuncias-mensagens").cookie(cookie(bia))).andExpect(status().isNotFound());
        mockMvc.perform(patch("/moderacao/denuncias-mensagens/1/remover-mensagem").cookie(cookie(bia)))
                .andExpect(status().isNotFound());
    }

    @Test
    void clienteComConversasEDenunciasApagaConta() throws Exception {
        ChefeModel caio = novoChefe("Caio");
        long comBia = abrir(ana, bia).andReturnId();
        long comCaio = abrir(ana, caio).andReturnId();
        long daBia = new Resposta(enviar(bia, comBia, "oi Ana")).andReturnId();
        long daAna = new Resposta(enviar(ana, comCaio, "spam")).andReturnId();
        denunciar(ana, "/conversas/" + comBia + "/mensagens/" + daBia, "OFENSIVA").andExpect(status().isCreated());
        denunciar(caio, "/conversas/" + comCaio + "/mensagens/" + daAna, "SPAM").andExpect(status().isCreated());
        UserModel outra = novoCliente("Outra");
        long conversaDeOutra = abrir(outra, bia).andReturnId();
        enviar(outra, conversaDeOutra, "fico").andExpect(status().isCreated());

        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(ana);
        agendamento.setChefes(new ArrayList<>(java.util.List.of(bia)));
        agendamento.setQuantidadePessoas(2);
        agendamento.setStatus(StatusAgendamento.CONFIRMADO);
        agendamento = agendamentoRepository.save(agendamento);
        notificacaoService.notificar(bia.getId(), TipoUsuario.CHEFE, EventoNotificacao.AGENDAMENTO_CRIADO,
                "Ana pediu um agendamento", String.valueOf(agendamento.getId()));
        Denuncia denuncia = new Denuncia();
        denuncia.setCliente(ana);
        denuncia.setChefe(bia);
        denuncia.setAgendamento(agendamento);
        denuncia.setMotivo(MotivoDenuncia.COMPORTAMENTO);
        denuncia.setDescricao("atrasou");
        denuncia = denunciaRepository.save(denuncia);
        notificacaoService.notificar(moderacaoRegistry.idDe("moderacao@chefhome.test"), TipoUsuario.MODERADOR,
                EventoNotificacao.DENUNCIA_CRIADA, "Nova denúncia contra o chef Bia", String.valueOf(denuncia.getId()));
        // mesmo número de uma conversa apagada, mas de outro tipo (agendamento de outro cliente): tem de ficar
        // os ids dependem da ordem dos testes: das duas conversas apagadas (ids distintos), usa a que não
        // coincide com o agendamento da Ana, senão a notificação "de outro tipo" seria dela de verdade
        String idDeConversaApagada = String.valueOf(comBia == agendamento.getId() ? comCaio : comBia);
        notificacaoService.notificar(bia.getId(), TipoUsuario.CHEFE, EventoNotificacao.AGENDAMENTO_CRIADO,
                "Outra pediu um agendamento", idDeConversaApagada);
        java.util.List<Long> notificacoesQueFicam = notificacaoRepository.findAll().stream()
                .filter(n -> (n.getReferenciaId().equals(String.valueOf(conversaDeOutra))
                        && n.getEvento() == EventoNotificacao.MENSAGEM_RECEBIDA)
                        || (n.getReferenciaId().equals(idDeConversaApagada) && n.getEvento() == EventoNotificacao.AGENDAMENTO_CRIADO))
                .map(n -> n.getId()).toList();
        assertThat(notificacoesQueFicam).hasSize(2);

        mockMvc.perform(delete("/usuario/me").cookie(cookie(ana))).andExpect(status().isNoContent());

        // LGPD: nada que cite a Ana ou aponte para dados dela fica para trás, nem nas notificações dos outros
        assertThat(notificacaoRepository.findAll()).extracting("id").containsExactlyInAnyOrderElementsOf(notificacoesQueFicam);
        assertThat(denunciaRepository.count()).isZero();
        assertThat(usuarioRepository.findById(ana.getId())).isEmpty();
        assertThat(conversaRepository.findAll()).extracting("id").containsExactly(conversaDeOutra);
        assertThat(mensagemRepository.findAll()).extracting("texto").containsExactly("fico");
        assertThat(denunciaMensagemRepository.count()).isZero();
        assertThat(chefeRepository.findById(bia.getId())).isPresent();
    }

    // ---------------------------------------------------------------------

    ResultActions denunciar(UserModel cliente, String mensagemPath, String motivo) throws Exception {
        return denunciar(cookie(cliente), mensagemPath, motivo);
    }

    ResultActions denunciar(ChefeModel chefe, String mensagemPath, String motivo) throws Exception {
        return denunciar(cookie(chefe), mensagemPath, motivo);
    }

    ResultActions denunciar(Cookie quem, String mensagemPath, String motivo) throws Exception {
        return mockMvc.perform(post(mensagemPath + "/denuncias").cookie(quem).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"" + motivo + "\"}"));
    }

    Cookie cookieModerador() {
        String email = "moderacao@chefhome.test";
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(
                new UsuarioAutenticado(moderacaoRegistry.idDe(email), email, TipoUsuario.MODERADOR)));
    }

    record Resposta(ResultActions acoes) {
        Resposta andExpect(org.springframework.test.web.servlet.ResultMatcher m) throws Exception {
            acoes.andExpect(m);
            return this;
        }

        long andReturnId() throws Exception {
            return JSON.readTree(acoes.andReturn().getResponse().getContentAsString()).get("id").asLong();
        }
    }

    Resposta abrir(UserModel cliente, ChefeModel chefe) throws Exception {
        return new Resposta(mockMvc.perform(post("/conversas").cookie(cookie(cliente)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"chefeId\":\"" + chefe.getId() + "\"}")));
    }

    ResultActions enviar(UserModel cliente, long conversa, String texto) throws Exception {
        return enviar(cookie(cliente), conversa, texto);
    }

    ResultActions enviar(ChefeModel chefe, long conversa, String texto) throws Exception {
        return enviar(cookie(chefe), conversa, texto);
    }

    ResultActions enviar(Cookie quem, long conversa, String texto) throws Exception {
        return mockMvc.perform(post("/conversas/" + conversa + "/mensagens").cookie(quem)
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(java.util.Map.of("texto", texto))));
    }

    UserModel novoCliente(String nome) {
        UserModel usuario = new UserModel();
        usuario.setNome(nome);
        usuario.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        usuario.setEndereco(new ArrayList<>());
        return usuarioRepository.save(TelefoneDeTeste.verificado(usuario));
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
        return chefeRepository.save(TelefoneDeTeste.verificado(chefe));
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
