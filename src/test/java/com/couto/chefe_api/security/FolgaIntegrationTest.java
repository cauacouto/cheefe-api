package com.couto.chefe_api.security;

import com.couto.chefe_api.MenuDeTeste;
import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.Menu;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendaBloqueioRepository;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.MenuRepository;
import com.couto.chefe_api.repository.PratosRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Folga do chef entre agendamentos confirmados: criar, editar e confirmar respeitam a folga; bloqueios não. */
@SpringBootTest
@ActiveProfiles("test")
class FolgaIntegrationTest {

    static final LocalDateTime BASE = LocalDateTime.now().plusDays(10).withHour(18).withMinute(0).withSecond(0).withNano(0);
    static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired AgendaBloqueioRepository bloqueioRepository;
    @Autowired MenuRepository menuRepository;
    @Autowired PratosRepository pratosRepository;
    @Autowired MenuDeTeste menus;

    MockMvc mockMvc;
    ChefeModel bia;
    UserModel ana;
    long enderecoDaAna;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bia = novoChefe("Bia");
        ana = novoCliente("Ana");
        enderecoDaAna = novoEndereco(ana).getId();
    }

    @AfterEach
    void limparBanco() {
        agendamentoRepository.deleteAll();
        pratosRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    // ─── criar ───────────────────────────────────────────────────────────

    @Test
    void pedidoDentroDaFolgaDepoisDa409() throws Exception {
        novoAgendamento(novoCliente("Edu"), BASE, StatusAgendamento.CONFIRMADO); // 18h-20h, folga até 21h
        pedir(BASE.plusMinutes(150)).andExpect(status().isConflict()); // 20h30
    }

    @Test
    void pedidoDentroDaFolgaAntesDa409() throws Exception {
        novoAgendamento(novoCliente("Edu"), BASE, StatusAgendamento.CONFIRMADO); // folga desde 17h
        pedir(BASE.minusMinutes(150)).andExpect(status().isConflict()); // 15h30-17h30
    }

    @Test
    void encostarNaBordaDaFolgaNaoConflita() throws Exception {
        novoAgendamento(novoCliente("Edu"), BASE, StatusAgendamento.CONFIRMADO);
        pedir(BASE.plusHours(3)).andExpect(status().isCreated());  // começa às 21h, quando a folga acaba
        pedir(BASE.minusHours(3)).andExpect(status().isCreated()); // 15h-17h, termina quando a folga começa
    }

    @Test
    void folgaZeroIgualAoComportamentoAtual() throws Exception {
        novoAgendamento(novoCliente("Edu"), BASE, StatusAgendamento.CONFIRMADO);
        definirFolga(bia, 0);

        pedir(BASE.plusHours(2)).andExpect(status().isCreated());  // 20h, logo depois do confirmado
        pedir(BASE.plusHours(1)).andExpect(status().isConflict()); // sobrepor continua proibido
    }

    // ─── editar e confirmar ──────────────────────────────────────────────

    @Test
    void confirmarDentroDaFolgaDa409() throws Exception {
        novoAgendamento(novoCliente("Edu"), BASE, StatusAgendamento.CONFIRMADO);
        Agendamento pendente = novoAgendamento(ana, BASE.plusMinutes(150), StatusAgendamento.PENDENTE);

        mockMvc.perform(patch("/agendamentos/" + pendente.getId() + "/confirmar").cookie(cookieDe(bia)))
                .andExpect(status().isConflict());
        assertThat(agendamentoRepository.findById(pendente.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.PENDENTE);
    }

    @Test
    void putDeHorarioDentroDaFolgaDa409() throws Exception {
        novoAgendamento(novoCliente("Edu"), BASE, StatusAgendamento.CONFIRMADO);
        Agendamento pendente = novoAgendamento(ana, BASE.plusDays(1), StatusAgendamento.PENDENTE);

        mockMvc.perform(put("/agendamentos/" + pendente.getId()).cookie(cookieDe(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataHoraInicial\":\"" + BASE.plusMinutes(150).format(FORMATO) + "\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/agendamentos/" + pendente.getId()).cookie(cookieDe(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataHoraInicial\":\"" + BASE.plusHours(3).format(FORMATO) + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void oProprioAgendamentoNaoConflitaComSigoMesmo() throws Exception {
        // sozinho na agenda, o pedido é confirmado mesmo com a folga: ele não se mede contra si
        Agendamento pendente = novoAgendamento(ana, BASE, StatusAgendamento.PENDENTE);

        mockMvc.perform(patch("/agendamentos/" + pendente.getId() + "/confirmar").cookie(cookieDe(bia)))
                .andExpect(status().isOk());
    }

    // ─── o que a folga não alcança ───────────────────────────────────────

    @Test
    void bloqueioEDiaFixoNaoUsamFolga() throws Exception {
        bloquear(BASE.minusHours(2), BASE).andExpect(status().isCreated());
        pedir(BASE).andExpect(status().isCreated()); // encosta no bloqueio, sem folga

        // dia fixo no dia seguinte: um pedido que termina à meia-noite não é barrado por folga
        mockMvc.perform(put("/chefe/me/agenda/dias-fixos").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dias\":[\"" + BASE.plusDays(1).getDayOfWeek().name() + "\"]}"))
                .andExpect(status().isOk());
        pedir(BASE.toLocalDate().atTime(22, 0)).andExpect(status().isCreated());
    }

    @Test
    void agendaPublicaMostraConfirmadoComFolga() throws Exception {
        LocalDate dia = BASE.toLocalDate();
        novoAgendamento(novoCliente("Edu"), BASE, StatusAgendamento.CONFIRMADO); // 18h-20h -> 17h-21h
        // termina à meia-noite: está fora da janela, mas a folga entra nela e aparece recortada
        novoAgendamento(novoCliente("Zé"), dia.minusDays(1).atTime(22, 0), StatusAgendamento.CONFIRMADO);

        mockMvc.perform(get("/chefe/" + bia.getId() + "/agenda")
                        .param("de", dia.format(DIA)).param("ate", dia.format(DIA))
                        .cookie(cookieDe(novoCliente("Zeca"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].inicio").value(dia.atStartOfDay().format(FORMATO)))
                .andExpect(jsonPath("$[0].fim").value(dia.atTime(1, 0).format(FORMATO)))
                .andExpect(jsonPath("$[1].inicio").value(dia.atTime(17, 0).format(FORMATO)))
                .andExpect(jsonPath("$[1].fim").value(dia.atTime(21, 0).format(FORMATO)));
    }

    @Test
    void mudarAFolgaNaoDerrubaConfirmados() throws Exception {
        Agendamento primeiro = novoAgendamento(novoCliente("Edu"), BASE, StatusAgendamento.CONFIRMADO);
        Agendamento segundo = novoAgendamento(novoCliente("Zé"), BASE.plusHours(3), StatusAgendamento.CONFIRMADO);

        definirFolga(bia, 240);

        assertThat(agendamentoRepository.findById(primeiro.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.CONFIRMADO);
        assertThat(agendamentoRepository.findById(segundo.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.CONFIRMADO);
    }

    // ─── perfil ──────────────────────────────────────────────────────────

    @Test
    void patchDeFolgaInvalidaDa400() throws Exception {
        for (String invalida : new String[]{"-15", "255", "50"}) {
            mockMvc.perform(patch("/chefe/me").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"folgaMinutos\":" + invalida + "}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(chefeRepository.findById(bia.getId()).orElseThrow().getFolgaMinutos()).isEqualTo(60);
    }

    @Test
    void getChefeMeTrazFolga() throws Exception {
        mockMvc.perform(get("/chefe/me").cookie(cookieDe(bia)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.folgaMinutos").value(60));

        definirFolga(bia, 90);
        mockMvc.perform(get("/chefe/me").cookie(cookieDe(bia)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.folgaMinutos").value(90));
    }

    // ─── apoio ───────────────────────────────────────────────────────────

    void definirFolga(ChefeModel chefe, int minutos) throws Exception {
        mockMvc.perform(patch("/chefe/me").cookie(cookieDe(chefe)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folgaMinutos\":" + minutos + "}"))
                .andExpect(status().isOk());
    }

    ResultActions bloquear(LocalDateTime inicio, LocalDateTime fim) throws Exception {
        return mockMvc.perform(post("/chefe/me/agenda/bloqueios").cookie(cookieDe(bia)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"inicio\":\"" + inicio.format(FORMATO) + "\",\"fim\":\"" + fim.format(FORMATO) + "\"}"));
    }

    /** Pedido da Ana pelo menu de 2h da Bia. */
    ResultActions pedir(LocalDateTime inicio) throws Exception {
        Menu menu = menuRepository.findByChefe_IdOrderByIdAsc(bia.getId()).get(0);
        return mockMvc.perform(post("/agendamentos").cookie(cookieDe(ana)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuId\":" + menu.getId() + ",\"dataHoraInicial\":\"" + inicio.format(FORMATO)
                        + "\",\"enderecoId\":" + enderecoDaAna + ",\"quantidadePessoas\":2,\"ocasiao\":\"FAMILIA\"}"));
    }

    /** Agendamento de 2h com o menu da Bia (o PUT precisa do menu). */
    Agendamento novoAgendamento(UserModel cliente, LocalDateTime inicio, StatusAgendamento status) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(cliente == ana ? enderecoRepository.findById(enderecoDaAna).orElseThrow() : novoEndereco(cliente));
        agendamento.setChefe(bia);
        agendamento.setDataHoraInicial(inicio);
        agendamento.setDataHoraFinal(inicio.plusHours(2));
        agendamento.setQuantidadePessoas(2);
        agendamento.setValorTotal(new BigDecimal("200.00"));
        agendamento.setStatus(status);
        agendamento.aplicarMenu(menuRepository.findByChefe_IdOrderByIdAsc(bia.getId()).get(0), 2);
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
        chefe = chefeRepository.save(TelefoneDeTeste.verificado(chefe));
        menus.ativo(chefe);
        return chefe;
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
