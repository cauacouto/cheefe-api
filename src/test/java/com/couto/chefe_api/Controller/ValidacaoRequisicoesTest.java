package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Excepitons.GlobalExceptionHandler;
import com.couto.chefe_api.Services.ChefeService;
import com.couto.chefe_api.Services.EnderecoService;
import com.couto.chefe_api.Services.PratosService;
import com.couto.chefe_api.Services.agendamentoService;
import com.couto.chefe_api.User.UsuarioService;
import com.couto.chefe_api.Security.AuthCookies;
import com.couto.chefe_api.Security.UsuarioAutenticado;
import com.couto.chefe_api.enums.TipoUsuario;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garante que os controllers acionam a Bean Validation dos DTOs (@Valid) e que
 * o erro sai como 400 com os campos inválidos, sem chegar no serviço.
 */
class ValidacaoRequisicoesTest {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final agendamentoService agendamentoService = mock(agendamentoService.class);
    private final UsuarioService usuarioService = mock(UsuarioService.class);
    private final EnderecoService enderecoService = mock(EnderecoService.class);
    private final ChefeService chefeService = mock(ChefeService.class);
    private final PratosService pratosService = mock(PratosService.class);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new AgendamentoController(agendamentoService),
                        new UsuarioController(usuarioService, enderecoService, new AuthCookies(false)),
                        new ChefeController(chefeService, new AuthCookies(false)),
                        new PratosController(pratosService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.randomUUID(), "ana@x.com", TipoUsuario.CLIENTE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void criarAgendamentoSemCamposObrigatoriosDa400() throws Exception {
        mvc.perform(post("/agendamentos").param("email", "ana@x.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chefeIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].campo").value(hasItems(
                        "dataHoraInicial", "dataHoraFinal", "enderecoId", "chefeIds", "quantidadePessoas")));
        verifyNoInteractions(agendamentoService);
    }

    @Test
    void criarAgendamentoNoPassadoOuSemPessoasDa400() throws Exception {
        String passado = LocalDateTime.now().minusDays(1).format(FORMATO);
        String json = """
                {"dataHoraInicial":"%s","dataHoraFinal":"%s","enderecoId":1,
                 "chefeIds":["%s"],"quantidadePessoas":0}
                """.formatted(passado, passado, UUID.randomUUID());

        mvc.perform(post("/agendamentos").param("email", "ana@x.com")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].campo").value(hasItems(
                        "dataHoraInicial", "dataHoraFinal", "quantidadePessoas")));
        verifyNoInteractions(agendamentoService);
    }

    @Test
    void criarAgendamentoValidoChegaNoServico() throws Exception {
        LocalDateTime inicio = LocalDateTime.now().plusDays(3);
        String json = """
                {"dataHoraInicial":"%s","dataHoraFinal":"%s","enderecoId":1,
                 "chefeIds":["%s"],"quantidadePessoas":4}
                """.formatted(inicio.format(FORMATO), inicio.plusHours(3).format(FORMATO), UUID.randomUUID());

        mvc.perform(post("/agendamentos").param("email", "ana@x.com")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        verify(agendamentoService).criarAgendamento(any(), any());
    }

    @Test
    void atualizarAgendamentoAceitaParcialMasValidaOsCamposEnviados() throws Exception {
        mvc.perform(put("/agendamentos/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidadePessoas\":0,\"chefeIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].campo").value(hasItems("quantidadePessoas", "chefeIds")));

        mvc.perform(put("/agendamentos/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidadePessoas\":3}"))
                .andExpect(status().isOk());
        verify(agendamentoService).atualizarAgedamento(any(), anyLong());
    }

    @Test
    void atualizarUsuarioValidaEnderecosAninhados() throws Exception {
        String json = """
                {"nome":"Ana","telefone":"11999999999",
                 "endereco":[{"rua":"Rua A","numero":"1","cep":"123","cidade":"SP","bairro":"Centro",
                              "estado":"SP","tipoDeResidencia":"CASA"}]}
                """;

        mvc.perform(put("/usuario/me")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].campo").value(hasItems("endereco[0].cep")));
        verifyNoInteractions(usuarioService);
    }

    @Test
    void adicionarEnderecoValidaCampos() throws Exception {
        mvc.perform(post("/usuario/enderecos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rua\":\"\",\"cep\":\"01001-000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].campo").value(hasItems(
                        "rua", "numero", "cidade", "bairro", "estado", "tipoDeResidencia")));
        verifyNoInteractions(enderecoService);
    }

    @Test
    void atualizarChefeValidaSoOsCamposEnviados() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(patch("/chefe/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"  \",\"anosDeExperiencia\":-1,\"valorHora\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].campo").value(hasItems("nome", "anosDeExperiencia", "valorHora")));

        mvc.perform(patch("/chefe/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Novo nome\",\"anosDeExperiencia\":3}"))
                .andExpect(status().isOk());
        verify(chefeService).atualizarDados(any(), any());
    }

    @Test
    void cadastrarPratoValidaDadosAntesDoUpload() throws Exception {
        MockMultipartFile dados = new MockMultipartFile("dados", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"nomePrato\":\"\",\"descricao\":\"\"}".getBytes());
        MockMultipartFile foto = new MockMultipartFile("foto", "p.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1});

        mvc.perform(multipart("/pratos").file(dados).file(foto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].campo").value(hasItems("nomePrato", "descricao")));
        verifyNoInteractions(pratosService);
    }

    @Test
    void atualizarPratoValidaCampos() throws Exception {
        mvc.perform(put("/pratos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomePrato\":\"\",\"descricao\":\"ok\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].campo").value(hasItems("nomePrato")));
        verifyNoInteractions(pratosService);
    }
}
