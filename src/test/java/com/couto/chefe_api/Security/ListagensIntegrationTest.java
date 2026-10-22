package com.couto.chefe_api.Security;

import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.domin.Pratos;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.AgendamentoRepository;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.EndercoRepository;
import com.couto.chefe_api.repositorys.PatrosRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Listagens: paginação, campos que o front precisa e só chefs visíveis no que é público. */
@SpringBootTest
@ActiveProfiles("test")
class ListagensIntegrationTest {

    static final LocalDateTime BASE = LocalDateTime.now().plusDays(10).withHour(18).withMinute(0).withSecond(0).withNano(0);

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EndercoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired PatrosRepository pratosRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void limparBanco() {
        agendamentoRepository.deleteAll();
        pratosRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void meusAgendamentosPaginadosComADataMaisDistantePrimeiro() throws Exception {
        UserModel ana = novoCliente("Ana");
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        novoAgendamento(ana, BASE.plusDays(1), bia);
        Agendamento maisDistante = novoAgendamento(ana, BASE.plusDays(5), bia, caio);
        novoAgendamento(ana, BASE.plusDays(3), caio);

        mockMvc.perform(get("/agendamentos/meus").param("size", "2").cookie(cookieDe(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(maisDistante.getId()))
                .andExpect(jsonPath("$.content[0].valorTotal").value(200.0))
                .andExpect(jsonPath("$.content[0].dataHoraFinal").isNotEmpty())
                .andExpect(jsonPath("$.content[0].chefes.length()").value(2))
                .andExpect(jsonPath("$.content[0].chefes[*].nome").value(org.hamcrest.Matchers.containsInAnyOrder("Bia", "Caio")))
                .andExpect(jsonPath("$.content[0].chefes[0].id").isNotEmpty());
    }

    @Test
    void recebidosPaginadosComValorEFimEOrdenacaoDoClienteIgnorada() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        novoAgendamento(novoCliente("Ana"), BASE.plusDays(2), bia);
        novoAgendamento(novoCliente("Edu"), BASE.plusDays(4), bia);

        mockMvc.perform(get("/agendamentos/recebidos").param("sort", "usuario.email").param("size", "1000")
                        .cookie(cookieDe(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.size").value(50))
                .andExpect(jsonPath("$.content[0].nomeUsuario").value("Edu"))
                .andExpect(jsonPath("$.content[0].valorTotal").value(200.0))
                .andExpect(jsonPath("$.content[0].dataHoraFinal").isNotEmpty());
    }

    @Test
    void pratosPublicosSoDeChefsVisiveisComFotoEId() throws Exception {
        ChefeModel visivel = novoChefe("Bia");
        ChefeModel banido = novoChefe("Caio");
        banido.setAtivo(false);
        chefeRepository.save(banido);
        ChefeModel pausado = novoChefe("Dani");
        pausado.setDisponivel(false);
        chefeRepository.save(pausado);
        Pratos moqueca = novoPrato(visivel, "Moqueca");
        novoPrato(banido, "Feijoada");
        novoPrato(pausado, "Risoto");
        Cookie ana = cookieDe(novoCliente("Ana"));

        mockMvc.perform(get("/pratos").cookie(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(moqueca.getId()))
                .andExpect(jsonPath("$.content[0].chefeId").value(visivel.getId().toString()))
                .andExpect(jsonPath("$.content[0].imageUrl").value("https://img.test/Moqueca.jpg"));
        mockMvc.perform(get("/pratos/chefe/" + visivel.getId()).cookie(ana))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].imageUrl").value("https://img.test/Moqueca.jpg"));
        mockMvc.perform(get("/pratos/chefe/" + banido.getId()).cookie(ana)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/pratos/chefe/" + pausado.getId()).cookie(ana)).andExpect(jsonPath("$.length()").value(0));
        // o próprio chef pausado continua vendo e editando os pratos dele
        mockMvc.perform(get("/pratos/meus").cookie(cookieDe(pausado))).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void chefEClienteTrazemAFoto() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        bia.setImageUrl("https://img.test/bia.jpg");
        chefeRepository.save(bia);
        UserModel ana = novoCliente("Ana");
        ana.setImageUrl("https://img.test/ana.jpg");
        usuarioRepository.save(ana);

        mockMvc.perform(get("/chefe").cookie(cookieDe(ana)))
                .andExpect(jsonPath("$.content[0].imageUrl").value("https://img.test/bia.jpg"));
        mockMvc.perform(get("/usuario/me").cookie(cookieDe(ana)))
                .andExpect(jsonPath("$.imageUrl").value("https://img.test/ana.jpg"));
    }

    @Test
    void destaqueSoComChefsVisiveisDoMaisAgendadoAoMenosEOrdenacaoDoClienteIgnorada() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        ChefeModel suspenso = novoChefe("Dani");
        suspenso.setSuspenso(true);
        chefeRepository.save(suspenso);
        UserModel ana = novoCliente("Ana");
        novoAgendamento(ana, BASE, caio);
        novoAgendamento(ana, BASE.plusDays(1), caio);
        novoAgendamento(ana, BASE.plusDays(2), bia);
        for (int i = 0; i < 5; i++) {
            novoAgendamento(ana, BASE.plusDays(10 + i), suspenso);
        }

        mockMvc.perform(get("/chefe/destaque").param("sort", "email").param("size", "1000").cookie(cookieDe(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].nome").value("Caio"))
                .andExpect(jsonPath("$.content[1].nome").value("Bia"))
                .andExpect(jsonPath("$.page.size").value(50));
    }

    @Test
    void buscaOrdenaSoPorCamposVisiveisELimitaAPagina() throws Exception {
        ChefeModel barato = novoChefe("Bia");
        ChefeModel caro = novoChefe("Caio");
        caro.setValorHora(new BigDecimal("300"));
        chefeRepository.save(caro);
        Cookie ana = cookieDe(novoCliente("Ana"));

        mockMvc.perform(get("/chefe").param("sort", "valorHora,desc").cookie(ana))
                .andExpect(jsonPath("$.content[0].nome").value("Caio"))
                .andExpect(jsonPath("$.content[1].nome").value("Bia"));
        mockMvc.perform(get("/chefe").param("sort", "valorHora,asc").cookie(ana))
                .andExpect(jsonPath("$.content[0].nome").value("Bia"));
        // campo que a resposta não mostra: ordenação ignorada (sem 500 e sem vazar a ordem dos e-mails)
        mockMvc.perform(get("/chefe").param("sort", "email").param("size", "1000").cookie(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.size").value(50));
    }

    // ---------------------------------------------------------------------

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
