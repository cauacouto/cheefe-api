package com.couto.chefe_api.Security;

import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.AgendamentoRepository;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.DenunciaRepository;
import com.couto.chefe_api.repositorys.EndercoRepository;
import com.couto.chefe_api.repositorys.NotificacaoRepository;
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
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class DenunciaIntegrationTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    TokenService tokenService;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    ChefeRepository chefeRepository;
    @Autowired
    EndercoRepository enderecoRepository;
    @Autowired
    AgendamentoRepository agendamentoRepository;
    @Autowired
    DenunciaRepository denunciaRepository;
    @Autowired
    NotificacaoRepository notificacaoRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void limparBanco() {
        denunciaRepository.deleteAll();
        agendamentoRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
        notificacaoRepository.deleteAll();
    }

    @Test
    void clienteAtendidoDenunciaEUmaSegundaDa409() throws Exception {
        UserModel cliente = novoCliente();
        ChefeModel chefe = novoChefe();
        Agendamento agendamento = agendamentoConfirmadoNoPassado(cliente, chefe);

        denunciar(cliente, agendamento, chefe, "\"descricao\":\"chegou atrasado e foi grosseiro\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTA"));
        denunciar(cliente, agendamento, chefe, "\"descricao\":\"de novo\"")
                .andExpect(status().isConflict());
    }

    @Test
    void chefNaoDenuncia() throws Exception {
        ChefeModel chefe = novoChefe();

        mockMvc.perform(post("/denuncias").cookie(cookie(chefe.getId(), chefe.getEmail(), TipoUsuario.CHEFE))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void semDescricaoDa400() throws Exception {
        UserModel cliente = novoCliente();
        ChefeModel chefe = novoChefe();
        Agendamento agendamento = agendamentoConfirmadoNoPassado(cliente, chefe);

        denunciar(cliente, agendamento, chefe, "\"descricao\":\"  \"").andExpect(status().isBadRequest());
    }

    @Test
    void chefSuspensoVeAvisoNoPerfilESomeDaBusca() throws Exception {
        ChefeModel chefe = novoChefe();
        chefe.setSuspenso(true);
        chefeRepository.save(chefe);
        Cookie cookie = cookie(chefe.getId(), chefe.getEmail(), TipoUsuario.CHEFE);

        mockMvc.perform(get("/chefe/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspenso").value(true))
                .andExpect(jsonPath("$.ocultoPorDenuncias").value(false));
        mockMvc.perform(get("/chefe").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    ResultActions denunciar(UserModel cliente, Agendamento agendamento, ChefeModel chefe, String descricao) throws Exception {
        return mockMvc.perform(post("/denuncias").cookie(cookie(cliente.getId(), cliente.getEmail(), TipoUsuario.CLIENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agendamentoId\":" + agendamento.getId() + ",\"chefeId\":\"" + chefe.getId()
                        + "\",\"motivo\":\"COMPORTAMENTO\"," + descricao + "}"));
    }

    UserModel novoCliente() {
        UserModel usuario = new UserModel();
        usuario.setNome("Cliente");
        usuario.setEmail("cliente-" + UUID.randomUUID() + "@x.com");
        usuario.setEndereco(new ArrayList<>());
        return usuarioRepository.save(usuario);
    }

    ChefeModel novoChefe() {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome("Bia");
        chefe.setEmail("chef-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("100"));
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        return chefeRepository.save(chefe);
    }

    Agendamento agendamentoConfirmadoNoPassado(UserModel cliente, ChefeModel chefe) {
        EnderecoModel endereco = new EnderecoModel();
        endereco.setRua("Rua A");
        endereco.setNumero("1");
        endereco.setBairro("Centro");
        endereco.setCidade("São Paulo");
        endereco.setEstado("SP");
        endereco.setCep("01001-000");
        endereco.setTipoDeResidencia(TipoDeResidencia.CASA);
        endereco.setUsuario(cliente);
        endereco = enderecoRepository.save(endereco);
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(endereco);
        agendamento.setChefes(new ArrayList<>(List.of(chefe)));
        agendamento.setDataHoraInicial(LocalDateTime.now().minusDays(3));
        agendamento.setDataHoraFinal(LocalDateTime.now().minusDays(3).plusHours(2));
        agendamento.setQuantidadePessoas(2);
        agendamento.setValorTotal(new BigDecimal("200"));
        agendamento.setStatus(StatusAgendamento.CONFIRMADO);
        return agendamentoRepository.save(agendamento);
    }

    Cookie cookie(UUID id, String email, TipoUsuario tipo) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(new UsuarioAutenticado(id, email, tipo)));
    }
}
