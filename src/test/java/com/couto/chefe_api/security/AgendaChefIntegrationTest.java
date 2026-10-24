package com.couto.chefe_api.security;

import com.couto.chefe_api.domain.AgendaBloqueio;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendaBloqueioRepository;
import com.couto.chefe_api.repository.AgendaDiaFixoRepository;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Agenda do chef: bloqueios avulsos, dias fixos da semana e a consulta pública. */
@SpringBootTest
@ActiveProfiles("test")
class AgendaChefIntegrationTest {

    static final LocalDateTime BASE = LocalDateTime.now().plusDays(10).withHour(18).withMinute(0).withSecond(0).withNano(0);
    static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String INDISPONIVEL = "Chef indisponível nesse horário";

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired AgendaBloqueioRepository bloqueioRepository;
    @Autowired AgendaDiaFixoRepository diaFixoRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void limparBanco() {
        agendamentoRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll(); // a agenda sai junto (ON DELETE CASCADE)
    }

    // ─── bloqueios ───────────────────────────────────────────────────────

    @Test
    void criaListaERemoveBloqueio() throws Exception {
        ChefeModel bia = novoChefe("Bia");

        ResultActions criado = bloquear(bia, BASE, BASE.plusDays(3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inicio").value(BASE.format(FORMATO)))
                .andExpect(jsonPath("$.fim").value(BASE.plusDays(3).format(FORMATO)));
        long id = JSON.readTree(criado.andReturn().getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/chefe/me/agenda").cookie(cookieDe(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueios.length()").value(1))
                .andExpect(jsonPath("$.bloqueios[0].id").value(id))
                .andExpect(jsonPath("$.diasFixos.length()").value(0));

        mockMvc.perform(delete("/chefe/me/agenda/bloqueios/" + id).cookie(cookieDe(bia))).andExpect(status().isNoContent());
        assertThat(bloqueioRepository.findAll()).isEmpty();
    }

    @Test
    void listaSoOsBloqueiosQueAindaNaoTerminaramPorInicio() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        gravarBloqueio(bia, LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(4)); // já terminou
        gravarBloqueio(bia, BASE.plusDays(5), BASE.plusDays(6));
        gravarBloqueio(bia, BASE, BASE.plusDays(1));

        mockMvc.perform(get("/chefe/me/agenda").cookie(cookieDe(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueios.length()").value(2))
                .andExpect(jsonPath("$.bloqueios[0].inicio").value(BASE.format(FORMATO)))
                .andExpect(jsonPath("$.bloqueios[1].inicio").value(BASE.plusDays(5).format(FORMATO)));
    }

    @Test
    void bloqueioInvalidoDa400() throws Exception {
        ChefeModel bia = novoChefe("Bia");

        bloquear(bia, BASE, BASE).andExpect(status().isBadRequest()); // fim = início
        bloquear(bia, BASE, BASE.minusHours(1)).andExpect(status().isBadRequest());
        bloquear(bia, LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(2)).andExpect(status().isBadRequest());
        bloquear(bia, BASE, BASE.plusDays(366)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/chefe/me/agenda/bloqueios").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inicio\":\"" + BASE.format(FORMATO) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/chefe/me/agenda/bloqueios").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inicio\":\"ontem\",\"fim\":\"amanhã\"}"))
                .andExpect(status().isBadRequest());
        // 365 dias exatos é o limite
        bloquear(bia, BASE, BASE.plusDays(365)).andExpect(status().isCreated());
    }

    @Test
    void bloqueioComecandoNoPassadoMasTerminandoNoFuturoEhValido() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        bloquear(bia, LocalDateTime.now().minusHours(2), LocalDateTime.now().plusDays(2)).andExpect(status().isCreated());
    }

    @Test
    void bloqueiosPodemSeSobreporEntreSi() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        bloquear(bia, BASE, BASE.plusDays(2)).andExpect(status().isCreated());
        bloquear(bia, BASE.plusDays(1), BASE.plusDays(3)).andExpect(status().isCreated());
    }

    @Test
    void bloqueioSobrepondoConfirmadoDa409ComIdEHorario() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        Agendamento confirmado = novoAgendamento(novoCliente("Edu"), BASE, StatusAgendamento.CONFIRMADO, bia); // 18h-20h

        bloquear(bia, BASE.minusHours(1), BASE.plusHours(1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Você tem agendamento confirmado nesse período")))
                .andExpect(jsonPath("$.detail").value(containsString(String.valueOf(confirmado.getId()))))
                .andExpect(jsonPath("$.detail").value(containsString(BASE.format(FORMATO))));
        assertThat(bloqueioRepository.findAll()).isEmpty();
    }

    @Test
    void bloqueioEncostandoOuSobrepondoPendenteEhAceito() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        novoAgendamento(novoCliente("Edu"), BASE, StatusAgendamento.CONFIRMADO, bia); // 18h-20h
        Agendamento pendente = novoAgendamento(novoCliente("Ana"), BASE.plusDays(1), StatusAgendamento.PENDENTE, bia);

        bloquear(bia, BASE.plusHours(2), BASE.plusHours(4)).andExpect(status().isCreated()); // começa quando o confirmado acaba
        bloquear(bia, BASE.minusHours(3), BASE).andExpect(status().isCreated());              // termina quando ele começa
        bloquear(bia, BASE.plusDays(1), BASE.plusDays(1).plusHours(2)).andExpect(status().isCreated()); // em cima do pendente

        assertThat(agendamentoRepository.findById(pendente.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.PENDENTE);
    }

    @Test
    void confirmadoDeOutroChefOuJaTerminadoNaoImpedeOBloqueio() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        novoAgendamento(novoCliente("Edu"), BASE, StatusAgendamento.CONFIRMADO, caio);
        novoAgendamento(novoCliente("Ana"), LocalDateTime.now().minusDays(1), StatusAgendamento.CONFIRMADO, bia); // já aconteceu

        bloquear(bia, BASE.minusHours(1), BASE.plusHours(1)).andExpect(status().isCreated());
        bloquear(bia, LocalDateTime.now().minusDays(2), LocalDateTime.now().plusDays(1)).andExpect(status().isCreated());
    }

    @Test
    void limiteDeCemBloqueiosFuturos() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        gravarBloqueio(bia, LocalDateTime.now().minusDays(9), LocalDateTime.now().minusDays(8)); // passado não conta
        for (int i = 0; i < 100; i++) {
            gravarBloqueio(bia, BASE.plusDays(i), BASE.plusDays(i).plusHours(1));
        }

        bloquear(bia, BASE.plusDays(200), BASE.plusDays(201)).andExpect(status().isConflict());
        // o limite é por chef
        bloquear(novoChefe("Caio"), BASE, BASE.plusHours(1)).andExpect(status().isCreated());
    }

    @Test
    void removerBloqueioDeOutroChefDa404() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        AgendaBloqueio dela = gravarBloqueio(bia, BASE, BASE.plusDays(1));

        mockMvc.perform(delete("/chefe/me/agenda/bloqueios/" + dela.getId()).cookie(cookieDe(caio)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/chefe/me/agenda/bloqueios/999999").cookie(cookieDe(bia))).andExpect(status().isNotFound());
        assertThat(bloqueioRepository.findAll()).hasSize(1);
    }

    @Test
    void rotasDaAgendaSaoSoDoChefAtivo() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Cookie cliente = cookieDe(ana);

        mockMvc.perform(get("/chefe/me/agenda")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/chefe/me/agenda").cookie(cliente)).andExpect(status().isForbidden());
        mockMvc.perform(post("/chefe/me/agenda/bloqueios").cookie(cliente).contentType(MediaType.APPLICATION_JSON)
                .content("{\"inicio\":\"" + BASE.format(FORMATO) + "\",\"fim\":\"" + BASE.plusDays(1).format(FORMATO) + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/chefe/me/agenda/bloqueios/1").cookie(cliente)).andExpect(status().isForbidden());
        mockMvc.perform(put("/chefe/me/agenda/dias-fixos").cookie(cliente).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dias\":[]}")).andExpect(status().isForbidden());

        bia.setAtivo(false); // conta apagada, token ainda válido
        chefeRepository.save(bia);
        mockMvc.perform(get("/chefe/me/agenda").cookie(cookieDe(bia))).andExpect(status().isForbidden());
        bloquear(bia, BASE, BASE.plusDays(1)).andExpect(status().isForbidden());
        diasFixos(bia, "MONDAY").andExpect(status().isForbidden());
    }

    // ─── dias fixos ──────────────────────────────────────────────────────

    @Test
    void definirDiasFixosSubstituiAListaELimpaComVazia() throws Exception {
        ChefeModel bia = novoChefe("Bia");

        diasFixos(bia, "MONDAY", "FRIDAY").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("MONDAY"))
                .andExpect(jsonPath("$[1]").value("FRIDAY"));
        mockMvc.perform(get("/chefe/me/agenda").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.diasFixos.length()").value(2));

        diasFixos(bia, "FRIDAY", "SUNDAY").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        assertThat(diaFixoRepository.findAll()).extracting(d -> d.getDiaSemana())
                .containsExactlyInAnyOrder(DayOfWeek.FRIDAY, DayOfWeek.SUNDAY);

        diasFixos(bia).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(diaFixoRepository.findAll()).isEmpty();
    }

    @Test
    void diasFixosInvalidosDao400() throws Exception {
        ChefeModel bia = novoChefe("Bia");

        diasFixos(bia, "MONDAY", "MONDAY").andExpect(status().isBadRequest());
        diasFixos(bia, "SEGUNDA").andExpect(status().isBadRequest());
        mockMvc.perform(put("/chefe/me/agenda/dias-fixos").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void diaFixoQuePegaConfirmadoFuturoDa409MasPassadoEPendenteNao() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel edu = novoCliente("Edu");
        novoAgendamento(edu, BASE, StatusAgendamento.CONFIRMADO, bia);
        novoAgendamento(edu, BASE.plusDays(1), StatusAgendamento.PENDENTE, bia);
        novoAgendamento(edu, LocalDateTime.now().minusDays(1), StatusAgendamento.CONFIRMADO, bia); // já aconteceu

        diasFixos(bia, BASE.getDayOfWeek().name())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Você tem agendamento confirmado nesse período")));
        assertThat(diaFixoRepository.findAll()).isEmpty();

        diasFixos(bia, BASE.plusDays(1).getDayOfWeek().name(), LocalDateTime.now().minusDays(1).getDayOfWeek().name())
                .andExpect(status().isOk());
    }

    @Test
    void diaFixoPegandoSoAParteDoConfirmadoQueVaiAteODiaSeguinteDa409() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        LocalDateTime noite = BASE.withHour(23); // 23h até 01h do dia seguinte
        Agendamento confirmado = novoAgendamento(novoCliente("Edu"), noite, StatusAgendamento.CONFIRMADO, bia);
        confirmado.setDataHoraFinal(noite.plusHours(2));
        agendamentoRepository.save(confirmado);

        diasFixos(bia, noite.plusDays(1).getDayOfWeek().name()).andExpect(status().isConflict());
        diasFixos(bia, noite.plusDays(2).getDayOfWeek().name()).andExpect(status().isOk());
    }

    // ─── aplicação nos agendamentos ──────────────────────────────────────

    @Test
    void agendarEmBloqueioDa409EEncostarNao() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        gravarBloqueio(bia, BASE, BASE.plusHours(3)); // 18h-21h
        UserModel ana = novoCliente("Ana");
        long endereco = novoEndereco(ana).getId();

        pedir(ana, endereco, BASE.plusHours(1), BASE.plusHours(2), bia)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(INDISPONIVEL));
        pedir(ana, endereco, BASE.minusHours(1), BASE.plusHours(1), bia).andExpect(status().isConflict());
        pedir(ana, endereco, BASE.minusHours(1), BASE.plusHours(4), bia).andExpect(status().isConflict());
        // encostando nos dois lados, ou com outro chef, passa
        pedir(ana, endereco, BASE.minusHours(2), BASE, bia).andExpect(status().isCreated());
        pedir(ana, endereco, BASE.plusHours(3), BASE.plusHours(5), bia).andExpect(status().isCreated());
        pedir(ana, endereco, BASE.plusHours(1), BASE.plusHours(2), caio).andExpect(status().isCreated());
    }

    @Test
    void comVariosChefsOErroCitaQuemEstaIndisponivel() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        gravarBloqueio(caio, BASE, BASE.plusHours(3));
        UserModel ana = novoCliente("Ana");

        pedir(ana, novoEndereco(ana).getId(), BASE.plusHours(1), BASE.plusHours(2), bia, caio)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Caio")))
                .andExpect(jsonPath("$.detail").value(containsString("indisponível nesse horário")));
    }

    @Test
    void agendarNumDiaFixoDa409EEmOutroDiaNao() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        diasFixos(bia, BASE.getDayOfWeek().name()).andExpect(status().isOk());
        UserModel ana = novoCliente("Ana");
        long endereco = novoEndereco(ana).getId();

        pedir(ana, endereco, BASE, BASE.plusHours(2), bia)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(INDISPONIVEL));
        // só a parte final cai no dia fixo: começa no dia anterior e passa da meia-noite
        pedir(ana, endereco, BASE.minusDays(1).withHour(23), BASE.withHour(1), bia).andExpect(status().isConflict());
        // termina exatamente à meia-noite do dia fixo: não toca nele
        pedir(ana, endereco, BASE.minusDays(1).withHour(22), BASE.withHour(0), bia).andExpect(status().isCreated());
        pedir(ana, endereco, BASE.plusDays(1), BASE.plusDays(1).plusHours(2), bia).andExpect(status().isCreated());
    }

    @Test
    void editarParaHorarioIndisponivelDa409() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        gravarBloqueio(bia, BASE, BASE.plusHours(3));
        UserModel ana = novoCliente("Ana");
        Agendamento daAna = novoAgendamento(ana, BASE.plusDays(1), StatusAgendamento.PENDENTE, bia);

        mockMvc.perform(put("/agendamentos/" + daAna.getId()).cookie(cookieDe(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataHoraInicial\":\"" + BASE.plusHours(1).format(FORMATO) + "\","
                                + "\"dataHoraFinal\":\"" + BASE.plusHours(2).format(FORMATO) + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(INDISPONIVEL));
        // sem mexer no horário a agenda não é reavaliada
        mockMvc.perform(put("/agendamentos/" + daAna.getId()).cookie(cookieDe(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidadePessoas\":4}"))
                .andExpect(status().isOk());
        // e um horário livre é aceito
        mockMvc.perform(put("/agendamentos/" + daAna.getId()).cookie(cookieDe(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataHoraInicial\":\"" + BASE.plusHours(3).format(FORMATO) + "\","
                                + "\"dataHoraFinal\":\"" + BASE.plusHours(5).format(FORMATO) + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void confirmarDentroDeBloqueioOuDiaFixoCriadoDepoisDa409EOPedidoContinuaPendente() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        Agendamento pedido = novoAgendamento(ana, BASE, StatusAgendamento.PENDENTE, bia);
        Agendamento outro = novoAgendamento(ana, BASE.plusDays(2), StatusAgendamento.PENDENTE, bia);
        // pendente no período continua depois do bloqueio / dia fixo
        bloquear(bia, BASE.minusHours(1), BASE.plusHours(1)).andExpect(status().isCreated());
        diasFixos(bia, BASE.plusDays(2).getDayOfWeek().name()).andExpect(status().isOk());

        mockMvc.perform(patch("/agendamentos/" + pedido.getId() + "/confirmar").cookie(cookieDe(bia)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(INDISPONIVEL));
        mockMvc.perform(patch("/agendamentos/" + outro.getId() + "/confirmar").cookie(cookieDe(bia)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(INDISPONIVEL));
        assertThat(agendamentoRepository.findById(pedido.getId()).orElseThrow().getStatus()).isEqualTo(StatusAgendamento.PENDENTE);

        // o chef recusa o pedido que não pode atender; com o bloqueio removido, o outro passa
        mockMvc.perform(patch("/agendamentos/" + pedido.getId() + "/recusar").cookie(cookieDe(bia)))
                .andExpect(status().isOk());
        diasFixos(bia).andExpect(status().isOk());
        mockMvc.perform(patch("/agendamentos/" + outro.getId() + "/confirmar").cookie(cookieDe(bia))).andExpect(status().isOk());
    }

    @Test
    void confirmarSoOlhaAAgendaDoChefQueConfirma() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        gravarBloqueio(caio, BASE, BASE.plusHours(3));
        Agendamento pedido = novoAgendamento(novoCliente("Ana"), BASE.plusHours(1), StatusAgendamento.PENDENTE, bia, caio);

        // a agenda de Caio não vale para a confirmação de Bia
        mockMvc.perform(patch("/agendamentos/" + pedido.getId() + "/confirmar").cookie(cookieDe(bia)))
                .andExpect(status().isOk());
    }

    // ─── consulta pública ────────────────────────────────────────────────

    @Test
    void consultaPublicaJuntaBloqueiosDiasFixosEConfirmadosEMescla() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        gravarBloqueio(bia, BASE, BASE.plusHours(2));                                  // D 18h-20h
        novoAgendamento(novoCliente("Edu"), BASE.plusHours(2), StatusAgendamento.CONFIRMADO, bia); // D 20h-22h (encosta)
        novoAgendamento(novoCliente("Ana"), BASE.plusHours(3), StatusAgendamento.PENDENTE, bia);   // pendente não aparece
        gravarBloqueio(bia, BASE.plusHours(3), BASE.plusHours(5));                     // D 21h-23h (sobrepõe)
        gravarBloqueio(bia, BASE.plusHours(4), BASE.plusHours(8));                     // D 22h - D+1 02h
        gravarBloqueio(bia, BASE.plusDays(5).withHour(10), BASE.plusDays(5).withHour(12));
        diasFixos(bia, BASE.plusDays(1).getDayOfWeek().name()).andExpect(status().isOk()); // D+1 inteiro

        LocalDate d = BASE.toLocalDate();
        ResultActions resposta = mockMvc.perform(get("/chefe/" + bia.getId() + "/agenda")
                        .param("de", d.minusDays(1).format(DIA)).param("ate", d.plusDays(6).format(DIA))
                        .cookie(cookieDe(novoCliente("Zeca"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].inicio").value(BASE.format(FORMATO)))
                .andExpect(jsonPath("$[0].fim").value(d.plusDays(2).atStartOfDay().format(FORMATO)))
                .andExpect(jsonPath("$[1].inicio").value(BASE.plusDays(5).withHour(10).format(FORMATO)))
                .andExpect(jsonPath("$[1].fim").value(BASE.plusDays(5).withHour(12).format(FORMATO)));
        // só início e fim: sem id, sem motivo, sem dizer o que é agendamento
        JsonNode primeiro = JSON.readTree(resposta.andReturn().getResponse().getContentAsString()).get(0);
        assertThat(primeiro.propertyNames()).containsExactlyInAnyOrder("inicio", "fim");
    }

    @Test
    void consultaPublicaRecortaNaJanelaEUsaPadraoDe30Dias() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        LocalDate hoje = LocalDate.now();
        gravarBloqueio(bia, hoje.plusDays(40).atTime(10, 0), hoje.plusDays(40).atTime(12, 0)); // fora do padrão
        gravarBloqueio(bia, hoje.plusDays(29).atTime(10, 0), hoje.plusDays(29).atTime(12, 0)); // dentro
        Cookie cookie = cookieDe(novoCliente("Zeca"));

        mockMvc.perform(get("/chefe/" + bia.getId() + "/agenda").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].inicio").value(hoje.plusDays(29).atTime(10, 0).format(FORMATO)));
        // janela de um dia só
        mockMvc.perform(get("/chefe/" + bia.getId() + "/agenda").param("de", hoje.plusDays(29).format(DIA))
                        .param("ate", hoje.plusDays(29).format(DIA)).cookie(cookie))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void janelaDaConsultaVaiAte90Dias() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        Cookie cookie = cookieDe(novoCliente("Zeca"));
        LocalDate hoje = LocalDate.now();

        mockMvc.perform(get("/chefe/" + bia.getId() + "/agenda").param("de", hoje.format(DIA))
                .param("ate", hoje.plusDays(90).format(DIA)).cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(get("/chefe/" + bia.getId() + "/agenda").param("de", hoje.format(DIA))
                .param("ate", hoje.plusDays(91).format(DIA)).cookie(cookie)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/chefe/" + bia.getId() + "/agenda").param("de", hoje.plusDays(3).format(DIA))
                .param("ate", hoje.plusDays(2).format(DIA)).cookie(cookie)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/chefe/" + bia.getId() + "/agenda").param("de", "amanhã").cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consultaPublicaDeChefNaoVisivelOuInexistenteDa404() throws Exception {
        Cookie cookie = cookieDe(novoCliente("Zeca"));
        ChefeModel pausado = novoChefe("Pausa");
        pausado.setDisponivel(false);
        chefeRepository.save(pausado);
        ChefeModel suspenso = novoChefe("Susp");
        suspenso.setSuspenso(true);
        chefeRepository.save(suspenso);
        ChefeModel incompleto = novoChefe("Incompleto");
        incompleto.setIdiomas(null);
        chefeRepository.save(incompleto);
        ChefeModel bloqueado = novoChefe("Bloq");
        bloqueado.setBloqueadoEm(LocalDateTime.now());
        chefeRepository.save(bloqueado);
        ChefeModel inativo = novoChefe("Inativo");
        inativo.setAtivo(false);
        chefeRepository.save(inativo);

        for (ChefeModel chefe : List.of(pausado, suspenso, incompleto, bloqueado, inativo)) {
            mockMvc.perform(get("/chefe/" + chefe.getId() + "/agenda").cookie(cookie)).andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/chefe/" + UUID.randomUUID() + "/agenda").cookie(cookie)).andExpect(status().isNotFound());
        mockMvc.perform(get("/chefe/" + novoChefe("Ok").getId() + "/agenda")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/chefe/" + novoChefe("Ok2").getId() + "/agenda").cookie(cookie)).andExpect(status().isOk());
    }

    // ─── apoio ───────────────────────────────────────────────────────────

    ResultActions bloquear(ChefeModel chefe, LocalDateTime inicio, LocalDateTime fim) throws Exception {
        return mockMvc.perform(post("/chefe/me/agenda/bloqueios").cookie(cookieDe(chefe)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"inicio\":\"" + inicio.format(FORMATO) + "\",\"fim\":\"" + fim.format(FORMATO) + "\"}"));
    }

    ResultActions diasFixos(ChefeModel chefe, String... dias) throws Exception {
        String lista = String.join(",", java.util.Arrays.stream(dias).map(d -> "\"" + d + "\"").toList());
        return mockMvc.perform(put("/chefe/me/agenda/dias-fixos").cookie(cookieDe(chefe)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dias\":[" + lista + "]}"));
    }

    ResultActions pedir(UserModel cliente, long enderecoId, LocalDateTime inicio, LocalDateTime fim, ChefeModel... chefes)
            throws Exception {
        String ids = String.join(",", java.util.Arrays.stream(chefes).map(c -> "\"" + c.getId() + "\"").toList());
        return mockMvc.perform(post("/agendamentos").cookie(cookieDe(cliente)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataHoraInicial\":\"" + inicio.format(FORMATO) + "\",\"dataHoraFinal\":\"" + fim.format(FORMATO)
                        + "\",\"enderecoId\":" + enderecoId + ",\"chefeIds\":[" + ids + "],\"quantidadePessoas\":2}"));
    }

    AgendaBloqueio gravarBloqueio(ChefeModel chefe, LocalDateTime inicio, LocalDateTime fim) {
        AgendaBloqueio bloqueio = new AgendaBloqueio();
        bloqueio.setChefe(chefe);
        bloqueio.setInicio(inicio);
        bloqueio.setFim(fim);
        bloqueio.setCriadoEm(LocalDateTime.now());
        return bloqueioRepository.save(bloqueio);
    }

    Agendamento novoAgendamento(UserModel cliente, LocalDateTime inicio, StatusAgendamento status, ChefeModel... chefes) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(novoEndereco(cliente));
        agendamento.setChefes(new ArrayList<>(List.of(chefes)));
        agendamento.setDataHoraInicial(inicio);
        agendamento.setDataHoraFinal(inicio.plusHours(2));
        agendamento.setQuantidadePessoas(2);
        agendamento.setValorTotal(new BigDecimal("200.00"));
        agendamento.setStatus(status);
        return agendamentoRepository.save(agendamento);
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
