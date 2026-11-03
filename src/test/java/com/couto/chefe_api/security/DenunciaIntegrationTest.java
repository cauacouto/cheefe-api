package com.couto.chefe_api.security;

import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.DenunciaRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    EnderecoRepository enderecoRepository;
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
    void denunciaDeAgendamentoConcluido() throws Exception {
        UserModel cliente = novoCliente();
        ChefeModel chefe = novoChefe();
        Agendamento agendamento = agendamentoConfirmadoNoPassado(cliente, chefe);
        // a rotina conclui o confirmado depois do fim: a denúncia não pode deixar de valer por isso
        agendamento.setStatus(com.couto.chefe_api.enums.StatusAgendamento.CONCLUIDO);
        agendamentoRepository.save(agendamento);

        denunciar(cliente, agendamento, chefe, "\"descricao\":\"não fez o combinado\"").andExpect(status().isCreated());
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

    @Test
    void clienteQueDenunciouConsegueApagarAConta() throws Exception {
        UserModel cliente = novoCliente();
        ChefeModel chefe = novoChefe();
        Agendamento agendamento = agendamentoConfirmadoNoPassado(cliente, chefe);
        denunciar(cliente, agendamento, chefe, "\"descricao\":\"grosseiro\"").andExpect(status().isCreated());

        mockMvc.perform(delete("/usuario/me").cookie(cookie(cliente.getId(), cliente.getEmail(), TipoUsuario.CLIENTE)))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(usuarioRepository.findById(cliente.getId())).isEmpty();
        org.assertj.core.api.Assertions.assertThat(denunciaRepository.count()).isZero();
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
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setEspecialidades(Set.of(Especialidades.BRASILEIRA));
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
        agendamento.setChefe(chefe);
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
