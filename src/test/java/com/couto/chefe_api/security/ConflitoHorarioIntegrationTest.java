package com.couto.chefe_api.security;

import com.couto.chefe_api.MenuDeTeste;
import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.domain.Menu;
import com.couto.chefe_api.repository.MenuRepository;
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
import java.util.Set;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lote 11: o chef não pode ter dois agendamentos CONFIRMADOS no mesmo horário. Pedidos pendentes
 * podem se sobrepor (o chef escolhe qual aceitar); encostar (um termina quando o outro começa) não conflita.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConflitoHorarioIntegrationTest {

    static final LocalDateTime BASE = LocalDateTime.now().plusDays(10).withHour(18).withMinute(0).withSecond(0).withNano(0);
    static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    @Autowired WebApplicationContext context;
    @Autowired TokenService tokenService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired PratosRepository pratosRepository;
    @Autowired MenuRepository menuRepository;
    @Autowired MenuDeTeste menus;

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
    void criarSobrepondoUmConfirmadoDoChefDa409() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        confirmar(novoAgendamento(novoCliente("Edu"), BASE, bia)); // 18h-20h confirmado
        UserModel ana = novoCliente("Ana");
        long endereco = novoEndereco(ana).getId();

        // o menu dura 2h: o fim é calculado
        pedir(ana, endereco, BASE.plusHours(1), bia).andExpect(status().isConflict());
        pedir(ana, endereco, BASE.minusHours(1), bia).andExpect(status().isConflict());
        // encostar não conflita, e outro chef no mesmo horário pode
        pedir(ana, endereco, BASE.plusHours(2), bia).andExpect(status().isCreated());
        pedir(ana, endereco, BASE.plusHours(1), caio).andExpect(status().isCreated());
    }

    @Test
    void pedidosPendentesPodemSeSobreporMasSoUmEhConfirmado() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        UserModel ana = novoCliente("Ana");
        UserModel edu = novoCliente("Edu");
        long daAna = idDe(pedir(ana, novoEndereco(ana).getId(), BASE, bia).andExpect(status().isCreated()));
        long doEdu = idDe(pedir(edu, novoEndereco(edu).getId(), BASE.plusHours(1), bia)
                .andExpect(status().isCreated()));

        mockMvc.perform(patch("/agendamentos/" + daAna + "/confirmar").cookie(cookieDe(bia))).andExpect(status().isOk());
        mockMvc.perform(patch("/agendamentos/" + doEdu + "/confirmar").cookie(cookieDe(bia)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Bia")));

        assertThat(agendamentoRepository.findById(doEdu).orElseThrow().getStatus().name()).isEqualTo("PENDENTE");
    }

    @Test
    void editarParaUmHorarioOcupadoDa409() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        confirmar(novoAgendamento(novoCliente("Edu"), BASE, bia));
        UserModel ana = novoCliente("Ana");
        long daAna = idDe(pedir(ana, novoEndereco(ana).getId(), BASE.plusDays(1), bia).andExpect(status().isCreated()));

        mockMvc.perform(put("/agendamentos/" + daAna).cookie(cookieDe(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataHoraInicial\":\"" + BASE.plusMinutes(30).format(FORMATO) + "\"}"))
                .andExpect(status().isConflict());
        // mudar só a quantidade de pessoas mantém o horário, que está livre
        mockMvc.perform(put("/agendamentos/" + daAna).cookie(cookieDe(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidadePessoas\":4}"))
                .andExpect(status().isOk());
    }

    @Test
    void confirmarSobrepostoAUmConfirmadoDoChefDa409() throws Exception {
        ChefeModel caio = novoChefe("Caio");
        confirmar(novoAgendamento(novoCliente("Edu"), BASE, caio));
        // pendente no mesmo horário em que o Caio já está confirmado
        Agendamento pendente = novoAgendamento(novoCliente("Ana"), BASE.plusHours(1), caio);

        mockMvc.perform(patch("/agendamentos/" + pendente.getId() + "/confirmar").cookie(cookieDe(caio)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O chef Caio já tem um agendamento confirmado nesse horário"));
    }

    @Test
    void confirmacoesSimultaneasDoMesmoHorarioSoUmaPassa() throws Exception {
        ChefeModel bia = novoChefe("Bia");
        List<Long> pedidos = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            pedidos.add(novoAgendamento(novoCliente("C" + i), BASE.plusMinutes(10L * i), bia).getId());
        }
        Cookie chef = cookieDe(bia);

        List<Integer> status = emParalelo(pedidos.stream()
                .<Callable<Integer>>map(id -> () -> mockMvc.perform(patch("/agendamentos/" + id + "/confirmar").cookie(chef))
                        .andReturn().getResponse().getStatus())
                .toList());

        assertThat(status).filteredOn(s -> s == 200).hasSize(1);
        assertThat(status).filteredOn(s -> s == 409).hasSize(5);
    }

    private void confirmar(Agendamento agendamento) {
        agendamento.setStatus(com.couto.chefe_api.enums.StatusAgendamento.CONFIRMADO);
        agendamentoRepository.save(agendamento);
    }

    private ResultActions pedir(UserModel cliente, long enderecoId, LocalDateTime inicio, ChefeModel chefe) throws Exception {
        Menu menu = menuRepository.findByChefe_IdOrderByIdAsc(chefe.getId()).stream().findFirst()
                .orElseGet(() -> menus.ativo(chefe));
        return mockMvc.perform(post("/agendamentos").cookie(cookieDe(cliente)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuId\":" + menu.getId() + ",\"dataHoraInicial\":\"" + inicio.format(FORMATO)
                        + "\",\"enderecoId\":" + enderecoId + ",\"quantidadePessoas\":2,\"ocasiao\":\"FAMILIA\"}"));
    }

    private static long idDe(ResultActions resposta) throws Exception {
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(resposta.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static <T> List<T> emParalelo(List<Callable<T>> pedidos) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(pedidos.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (Callable<T> pedido : pedidos) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return pedido.call();
                }));
            }
            largada.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------------

    Agendamento novoAgendamento(UserModel cliente, LocalDateTime inicio, ChefeModel chefe) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(novoEndereco(cliente));
        agendamento.setChefe(chefe);
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
