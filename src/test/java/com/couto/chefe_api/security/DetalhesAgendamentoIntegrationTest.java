package com.couto.chefe_api.security;

import com.couto.chefe_api.FiltroDeTeste;
import com.couto.chefe_api.MenuDeTeste;
import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.Notificacao;
import com.couto.chefe_api.domain.RestricaoAgendamento;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.AcaoAgendamento;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.OcasiaoMenu;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoRestricao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendamentoHistoricoRepository;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.BloqueioContaRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.NotificacaoRepository;
import com.couto.chefe_api.repository.PratosRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O cliente confirma ou ajusta restrições e observações de um agendamento confirmado até 48h antes (relógio fixo). */
@SpringBootTest
@Import({FiltroDeTeste.class, DetalhesAgendamentoIntegrationTest.RelogioFixo.class})
@ActiveProfiles("test")
class DetalhesAgendamentoIntegrationTest {

    /** Minutos cheios: o formato da API não tem segundos. */
    static final LocalDateTime AGORA = LocalDateTime.now().withSecond(0).withNano(0);
    static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    /** O serviço lê "agora" do Clock: fixo, o teste acerta a borda das 48h. */
    @TestConfiguration
    static class RelogioFixo {
        @Bean
        @Primary
        Clock relogioFixo() {
            ZoneId zona = ZoneId.systemDefault();
            return Clock.fixed(AGORA.atZone(zona).toInstant(), zona);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired AgendamentoHistoricoRepository historicoRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired BloqueioContaRepository bloqueioContaRepository;
    @Autowired PratosRepository pratosRepository;
    @Autowired MenuDeTeste menus;

    MockMvc mockMvc;
    ChefeModel bia;
    UserModel ana;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bia = novoChefe("Bia");
        ana = novoCliente("Ana Souza");
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
    void ajustarAte48hAntesSubstituiEConfirma() throws Exception {
        Agendamento agendamento = confirmado(ana, AGORA.plusDays(5), "sem pimenta",
                new RestricaoAgendamento(TipoRestricao.VEGETARIANO, 1));

        ajustar(ana, agendamento.getId(),
                "{\"restricoes\":[{\"tipo\":\"SEM_GLUTEN\",\"quantidade\":2}],\"observacoes\":\"sem coentro\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restricoes.length()").value(1))
                .andExpect(jsonPath("$.restricoes[0].tipo").value("SEM_GLUTEN"))
                .andExpect(jsonPath("$.restricoes[0].quantidade").value(2))
                .andExpect(jsonPath("$.observacoes").value("sem coentro"))
                .andExpect(jsonPath("$.detalhesConfirmadosEm").value(AGORA.format(FORMATO)));

        Agendamento salvo = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        assertThat(salvo.getObservacoes()).isEqualTo("sem coentro");
        assertThat(salvo.getDetalhesConfirmadosEm()).isEqualTo(AGORA);
        assertThat(salvo.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
        mockMvc.perform(get("/agendamentos/meus").cookie(cookieDe(ana)))
                .andExpect(jsonPath("$.content[0].restricoes.length()").value(1))
                .andExpect(jsonPath("$.content[0].restricoes[0].tipo").value("SEM_GLUTEN"));

        assertThat(historicoRepository.findByAgendamento_IdOrderByOcorridoEmAscIdAsc(agendamento.getId()))
                .singleElement().satisfies(linha -> {
                    assertThat(linha.getAcao()).isEqualTo(AcaoAgendamento.DETALHES_ALTERADOS);
                    assertThat(linha.getAutorId()).isEqualTo(ana.getId());
                    assertThat(linha.getAutorTipo()).isEqualTo(TipoUsuario.CLIENTE);
                });
        assertThat(notificacoesDoChef()).singleElement().satisfies(n -> {
            assertThat(n.getEvento()).isEqualTo(EventoNotificacao.DETALHES_ALTERADOS);
            assertThat(n.getReferenciaId()).isEqualTo(String.valueOf(agendamento.getId()));
            assertThat(n.getMensagem()).isEqualTo("Ana alterou os detalhes do agendamento de "
                    + agendamento.getDataHoraInicial().format(DIA));
        });
    }

    @Test
    void confirmarAte48hAntes() throws Exception {
        Agendamento agendamento = confirmado(ana, AGORA.plusDays(5), "sem pimenta",
                new RestricaoAgendamento(TipoRestricao.VEGETARIANO, 1));

        confirmar(ana, agendamento.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalhesConfirmadosEm").value(AGORA.format(FORMATO)))
                .andExpect(jsonPath("$.observacoes").value("sem pimenta"))
                .andExpect(jsonPath("$.restricoes[0].tipo").value("VEGETARIANO"));

        assertThat(agendamentoRepository.findById(agendamento.getId()).orElseThrow().getDetalhesConfirmadosEm())
                .isEqualTo(AGORA);
        assertThat(historicoRepository.findByAgendamento_IdOrderByOcorridoEmAscIdAsc(agendamento.getId()))
                .singleElement().satisfies(linha -> {
                    assertThat(linha.getAcao()).isEqualTo(AcaoAgendamento.DETALHES_CONFIRMADOS);
                    assertThat(linha.getAutorId()).isEqualTo(ana.getId());
                });
        assertThat(notificacoesDoChef()).singleElement().satisfies(n -> {
            assertThat(n.getEvento()).isEqualTo(EventoNotificacao.DETALHES_CONFIRMADOS);
            assertThat(n.getReferenciaId()).isEqualTo(String.valueOf(agendamento.getId()));
            assertThat(n.getMensagem()).isEqualTo("Ana confirmou os detalhes do agendamento de "
                    + agendamento.getDataHoraInicial().format(DIA));
        });
    }

    @Test
    void confirmarDuasVezesNaoNotificaDeNovo() throws Exception {
        Agendamento agendamento = confirmado(ana, AGORA.plusDays(5), null);

        confirmar(ana, agendamento.getId()).andExpect(status().isOk());
        confirmar(ana, agendamento.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.detalhesConfirmadosEm").value(AGORA.format(FORMATO)));

        assertThat(notificacoesDoChef()).hasSize(1);
        assertThat(historicoRepository.findByAgendamento_IdOrderByOcorridoEmAscIdAsc(agendamento.getId())).hasSize(1);
    }

    @Test
    void depoisDe48hAntesDa409() throws Exception {
        // borda exata: faltando 48h, "agora < início − 48h" já é falso
        Agendamento naBorda = confirmado(ana, AGORA.plusHours(48), "sem pimenta");
        // um microssegundo a mais ainda é prazo
        Agendamento dentro = confirmado(ana, AGORA.plusHours(48).plusNanos(1000), "sem pimenta");

        ajustar(ana, naBorda.getId(), "{\"observacoes\":\"mudei\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Os detalhes só podem ser alterados até 48h antes"));
        confirmar(ana, naBorda.getId()).andExpect(status().isConflict());
        ajustar(ana, dentro.getId(), "{\"observacoes\":\"mudei\"}").andExpect(status().isOk());
        confirmar(ana, dentro.getId()).andExpect(status().isOk());

        Agendamento intacto = agendamentoRepository.findById(naBorda.getId()).orElseThrow();
        assertThat(intacto.getObservacoes()).isEqualTo("sem pimenta");
        assertThat(intacto.getDetalhesConfirmadosEm()).isNull();
        assertThat(historicoRepository.findByAgendamento_IdOrderByOcorridoEmAscIdAsc(naBorda.getId())).isEmpty();
    }

    @Test
    void foraDeConfirmadoDa409() throws Exception {
        for (StatusAgendamento status : List.of(StatusAgendamento.PENDENTE, StatusAgendamento.CANCELADO,
                StatusAgendamento.CONCLUIDO, StatusAgendamento.EXPIRADO, StatusAgendamento.RECUSADO)) {
            Agendamento agendamento = novoAgendamento(ana, AGORA.plusDays(5), status, "sem pimenta");

            ajustar(ana, agendamento.getId(), "{\"observacoes\":\"mudei\"}").andExpect(status().isConflict());
            confirmar(ana, agendamento.getId()).andExpect(status().isConflict());
            assertThat(agendamentoRepository.findById(agendamento.getId()).orElseThrow().getObservacoes())
                    .isEqualTo("sem pimenta");
        }
        assertThat(notificacoesDoChef()).isEmpty();
    }

    @Test
    void observacoesMascaradasEOriginalGuardado() throws Exception {
        Agendamento agendamento = confirmado(ana, AGORA.plusDays(5), null);

        String corpo = ajustar(ana, agendamento.getId(), "{\"observacoes\":\"sem coentro 21 98888-7777\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observacoes").value("sem coentro ***"))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("98888").doesNotContain("observacoesOriginal");
        Agendamento salvo = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        assertThat(salvo.getObservacoes()).isEqualTo("sem coentro ***");
        assertThat(salvo.getObservacoesOriginal()).isEqualTo("sem coentro 21 98888-7777");
    }

    @Test
    void odioNasObservacoesBloqueia() throws Exception {
        Agendamento agendamento = confirmado(ana, AGORA.plusDays(5), "sem pimenta");

        ajustar(ana, agendamento.getId(), "{\"observacoes\":\"seu odiento\"}").andExpect(status().isForbidden());

        assertThat(usuarioRepository.findById(ana.getId()).orElseThrow().getBloqueadoEm()).isNotNull();
        Agendamento intacto = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        assertThat(intacto.getObservacoes()).isEqualTo("sem pimenta");
        assertThat(intacto.getDetalhesConfirmadosEm()).isNull();
        assertThat(notificacoesDoChef()).isEmpty();
    }

    @Test
    void deOutroClienteDa404SemModerar() throws Exception {
        Agendamento agendamento = confirmado(ana, AGORA.plusDays(5), "sem pimenta");
        UserModel caio = novoCliente("Caio");

        ajustar(caio, agendamento.getId(), "{\"observacoes\":\"seu odiento\"}").andExpect(status().isNotFound());
        confirmar(caio, agendamento.getId()).andExpect(status().isNotFound());

        assertThat(usuarioRepository.findById(caio.getId()).orElseThrow().getBloqueadoEm()).isNull();
        assertThat(bloqueioContaRepository.count()).isZero();
        assertThat(agendamentoRepository.findById(agendamento.getId()).orElseThrow().getDetalhesConfirmadosEm()).isNull();
        assertThat(notificacoesDoChef()).isEmpty();
    }

    @Test
    void chefNaRotaDa403() throws Exception {
        Agendamento agendamento = confirmado(ana, AGORA.plusDays(5), "sem pimenta");

        mockMvc.perform(patch("/agendamentos/" + agendamento.getId() + "/detalhes").cookie(cookieDe(bia))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/agendamentos/" + agendamento.getId() + "/detalhes/confirmar").cookie(cookieDe(bia)))
                .andExpect(status().isForbidden());
        assertThat(agendamentoRepository.findById(agendamento.getId()).orElseThrow().getDetalhesConfirmadosEm()).isNull();
    }

    @Test
    void campoAusenteValeComoVazio() throws Exception {
        Agendamento agendamento = confirmado(ana, AGORA.plusDays(5), "sem pimenta",
                new RestricaoAgendamento(TipoRestricao.VEGETARIANO, 1));

        // o PATCH substitui os dois campos: o que não vem é limpo, não preservado
        ajustar(ana, agendamento.getId(), "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restricoes.length()").value(0))
                .andExpect(jsonPath("$.observacoes").doesNotExist());

        Agendamento salvo = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        assertThat(salvo.getObservacoes()).isNull();
        assertThat(salvo.getObservacoesOriginal()).isNull();
        assertThat(salvo.getDetalhesConfirmadosEm()).isEqualTo(AGORA);
        mockMvc.perform(get("/agendamentos/meus").cookie(cookieDe(ana)))
                .andExpect(jsonPath("$.content[0].restricoes.length()").value(0));
    }

    @Test
    void restricaoAcimaDaQuantidadeDa400() throws Exception {
        Agendamento agendamento = confirmado(ana, AGORA.plusDays(5), "sem pimenta",
                new RestricaoAgendamento(TipoRestricao.VEGETARIANO, 1)); // 2 pessoas

        ajustar(ana, agendamento.getId(), "{\"restricoes\":[{\"tipo\":\"SEM_GLUTEN\",\"quantidade\":3}]}")
                .andExpect(status().isBadRequest());
        ajustar(ana, agendamento.getId(), "{\"restricoes\":[{\"tipo\":\"SEM_GLUTEN\",\"quantidade\":1},"
                + "{\"tipo\":\"SEM_GLUTEN\",\"quantidade\":2}]}").andExpect(status().isBadRequest());
        ajustar(ana, agendamento.getId(), "{\"observacoes\":\"" + "a".repeat(1001) + "\"}")
                .andExpect(status().isBadRequest());

        Agendamento intacto = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        assertThat(intacto.getObservacoes()).isEqualTo("sem pimenta");
        assertThat(intacto.getDetalhesConfirmadosEm()).isNull();
        mockMvc.perform(get("/agendamentos/meus").cookie(cookieDe(ana)))
                .andExpect(jsonPath("$.content[0].restricoes[0].tipo").value("VEGETARIANO"));
        assertThat(notificacoesDoChef()).isEmpty();
    }

    @Test
    void respostasTrazemDetalhesConfirmadosEm() throws Exception {
        Agendamento agendamento = confirmado(ana, AGORA.plusDays(5), null);

        // nulo até o cliente agir
        mockMvc.perform(get("/agendamentos/meus").cookie(cookieDe(ana)))
                .andExpect(jsonPath("$.content[0].detalhesConfirmadosEm").value(nullValue()));
        mockMvc.perform(get("/agendamentos/recebidos").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.content[0].detalhesConfirmadosEm").value(nullValue()));

        confirmar(ana, agendamento.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.detalhesConfirmadosEm").value(AGORA.format(FORMATO)));
        mockMvc.perform(get("/agendamentos/meus").cookie(cookieDe(ana)))
                .andExpect(jsonPath("$.content[0].detalhesConfirmadosEm").value(AGORA.format(FORMATO)));
        mockMvc.perform(get("/agendamentos/recebidos").cookie(cookieDe(bia)))
                .andExpect(jsonPath("$.content[0].detalhesConfirmadosEm").value(AGORA.format(FORMATO)));
    }

    @Test
    void contaApagadaDoClienteApagaOsAvisosDoChef() throws Exception {
        Agendamento agendamento = confirmado(ana, AGORA.plusDays(5), null);
        Agendamento outro = confirmado(novoCliente("Caio"), AGORA.plusDays(6), null);
        ajustar(ana, agendamento.getId(), "{}").andExpect(status().isOk());
        confirmar(ana, agendamento.getId()).andExpect(status().isOk());
        confirmar(usuarioRepository.findById(outro.getUsuario().getId()).orElseThrow(), outro.getId()).andExpect(status().isOk());
        assertThat(notificacoesDoChef()).hasSize(2);

        mockMvc.perform(delete("/usuario/me").cookie(cookieDe(ana))).andExpect(status().isNoContent());

        // só o aviso do outro cliente fica: os da Ana citam o nome e o agendamento dela
        assertThat(notificacoesDoChef()).singleElement()
                .satisfies(n -> assertThat(n.getReferenciaId()).isEqualTo(String.valueOf(outro.getId())));
    }

    // ─── apoio ───────────────────────────────────────────────────────────

    ResultActions ajustar(UserModel cliente, Long id, String corpo) throws Exception {
        return mockMvc.perform(patch("/agendamentos/" + id + "/detalhes").cookie(cookieDe(cliente))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    ResultActions confirmar(UserModel cliente, Long id) throws Exception {
        return mockMvc.perform(patch("/agendamentos/" + id + "/detalhes/confirmar").cookie(cookieDe(cliente)));
    }

    List<Notificacao> notificacoesDoChef() {
        return notificacaoRepository.findAll().stream().filter(n -> n.getDestinatarioId().equals(bia.getId())).toList();
    }

    Agendamento confirmado(UserModel cliente, LocalDateTime inicio, String observacoes, RestricaoAgendamento... restricoes) {
        return novoAgendamento(cliente, inicio, StatusAgendamento.CONFIRMADO, observacoes, restricoes);
    }

    /** Agendamento de 2 pessoas pelo menu da Bia. */
    Agendamento novoAgendamento(UserModel cliente, LocalDateTime inicio, StatusAgendamento status, String observacoes,
                                RestricaoAgendamento... restricoes) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(novoEndereco(cliente));
        agendamento.setChefe(bia);
        agendamento.setStatus(status);
        agendamento.setOcasiao(OcasiaoMenu.FAMILIA);
        agendamento.setDataHoraInicial(inicio);
        agendamento.aplicarMenu(menus.ativo(bia), 2);
        agendamento.setObservacoes(observacoes);
        agendamento.getRestricoes().addAll(Set.of(restricoes));
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
        usuario.setEmail(nome.toLowerCase().replace(' ', '-') + "-" + UUID.randomUUID() + "@x.com");
        usuario.setEndereco(new ArrayList<>());
        return usuarioRepository.save(TelefoneDeTeste.verificado(usuario));
    }

    ChefeModel novoChefe(String nome) {
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
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        return chefeRepository.save(TelefoneDeTeste.verificado(chefe));
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
