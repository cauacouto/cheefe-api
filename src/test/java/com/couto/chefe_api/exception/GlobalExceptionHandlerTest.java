package com.couto.chefe_api.exception;

import jakarta.servlet.ServletException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ControllerDeTeste())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void recursoNaoEncontradoViraProblemDetail404() throws Exception {
        mvc.perform(get("/teste/chefe-nao-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("chefe não encontrado"));
    }

    @Test
    void conflitoVira409() throws Exception {
        mvc.perform(get("/teste/chefe-indisponivel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Um ou mais chefes não estão disponíveis ou ativos"));
    }

    @Test
    void regraDeNegocioVira400() throws Exception {
        mvc.perform(get("/teste/regra"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Apartamento exige complemento"));
    }

    @Test
    void respeitaResponseStatusDeExcecaoDeOutroModulo() throws Exception {
        mvc.perform(get("/teste/nao-autorizado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("token inválido"));
    }

    @Test
    void respeitaResponseStatusComReasonEHerdadoDaSuperclasse() throws Exception {
        mvc.perform(get("/teste/conflito-anotado"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("E-mail já cadastrado"));
    }

    @Test
    void respeitaResponseStatusException() throws Exception {
        mvc.perform(get("/teste/response-status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("proibido"));
    }

    @Test
    void respeitaErrorResponseCustomizado() throws Exception {
        mvc.perform(get("/teste/error-response"))
                .andExpect(status().is(HttpStatus.UNPROCESSABLE_CONTENT.value()))
                .andExpect(jsonPath("$.detail").value("detalhe do ErrorResponse"));
    }

    @Test
    void validacaoVira400ComCamposInvalidos() throws Exception {
        mvc.perform(post("/teste/validacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"\",\"quantidade\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.campos[*].campo").value(containsInAnyOrder("nome", "quantidade")));
    }

    @Test
    void jsonMalformadoVira400() throws Exception {
        mvc.perform(post("/teste/validacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{nome"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void violacaoDeIntegridadeVira409() throws Exception {
        mvc.perform(get("/teste/integridade"))
                .andExpect(status().isConflict());
    }

    @Test
    void erroInesperadoVira500SemVazarMensagem() throws Exception {
        mvc.perform(get("/teste/inesperado"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value(not(containsString("segredo"))));
    }

    @Test
    void naoEngoleExcecoesDoSpringSecurity() {
        ServletException erro = assertThrows(ServletException.class,
                () -> mvc.perform(get("/teste/acesso-negado")));
        assertThat(erro.getCause()).isInstanceOf(AccessDeniedException.class);
    }

    // ---------------------------------------------------------------------

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    static class NaoAutorizadoException extends RuntimeException {
        NaoAutorizadoException(String message) {
            super(message);
        }
    }

    @ResponseStatus(code = HttpStatus.CONFLICT, reason = "E-mail já cadastrado")
    static class EmailDuplicadoException extends RuntimeException {
    }

    static class EmailDuplicadoSubclasseException extends EmailDuplicadoException {
    }

    static class MeuErrorResponse extends RuntimeException implements org.springframework.web.ErrorResponse {
        @Override
        public org.springframework.http.HttpStatusCode getStatusCode() {
            return HttpStatus.UNPROCESSABLE_CONTENT;
        }

        @Override
        public org.springframework.http.ProblemDetail getBody() {
            return org.springframework.http.ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNPROCESSABLE_CONTENT, "detalhe do ErrorResponse");
        }
    }

    record DtoDeTeste(@NotBlank String nome, @Positive Integer quantidade) {
    }

    @RestController
    static class ControllerDeTeste {

        @GetMapping("/teste/chefe-nao-encontrado")
        void chefeNaoEncontrado() {
            throw new ChefeNaoEncontradoException();
        }

        @GetMapping("/teste/chefe-indisponivel")
        void chefeIndisponivel() {
            throw new ChefeIndisponivelException();
        }

        @GetMapping("/teste/regra")
        void regra() {
            throw new EnderecoInvalidoException("Apartamento exige complemento");
        }

        @GetMapping("/teste/nao-autorizado")
        void naoAutorizado() {
            throw new NaoAutorizadoException("token inválido");
        }

        @GetMapping("/teste/conflito-anotado")
        void conflitoAnotado() {
            throw new EmailDuplicadoSubclasseException();
        }

        @GetMapping("/teste/response-status")
        void responseStatus() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "proibido");
        }

        @GetMapping("/teste/error-response")
        void errorResponse() {
            throw new MeuErrorResponse();
        }

        @PostMapping("/teste/validacao")
        void validacao(@Valid @RequestBody DtoDeTeste dto) {
        }

        @GetMapping("/teste/integridade")
        void integridade() {
            throw new DataIntegrityViolationException("duplicate key");
        }

        @GetMapping("/teste/inesperado")
        void inesperado() {
            throw new IllegalStateException("segredo interno");
        }

        @GetMapping("/teste/acesso-negado")
        void acessoNegado() {
            throw new AccessDeniedException("negado");
        }
    }
}
