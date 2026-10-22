package com.couto.chefe_api.security;

import com.couto.chefe_api.config.infra.ViaCepClient;
import com.couto.chefe_api.dto.ViaCepDto;
import com.couto.chefe_api.exception.ServicoExternoIndisponivelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Busca de CEP: pública (formulário de cadastro), com o ViaCep simulado. */
@SpringBootTest
@ActiveProfiles("test")
class EnderecoCepIntegrationTest {

    @Autowired WebApplicationContext context;
    @MockitoBean ViaCepClient viaCepClient;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void semLoginDevolveOEndereco() throws Exception {
        when(viaCepClient.buscarPorCep("01001000"))
                .thenReturn(new ViaCepDto("01001-000", "Praça da Sé", "Sé", "São Paulo", "SP", null));

        mockMvc.perform(get("/enderecos/cep/01001-000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rua").value("Praça da Sé"))
                .andExpect(jsonPath("$.cidade").value("São Paulo"))
                .andExpect(jsonPath("$.estado").value("SP"));
    }

    @Test
    void cepInvalidoInexistenteEViaCepForaDoAr() throws Exception {
        mockMvc.perform(get("/enderecos/cep/123")).andExpect(status().isBadRequest());
        verify(viaCepClient, never()).buscarPorCep(anyString());

        when(viaCepClient.buscarPorCep("99999999")).thenReturn(new ViaCepDto(null, null, null, null, null, "true"));
        mockMvc.perform(get("/enderecos/cep/99999999")).andExpect(status().isNotFound());

        when(viaCepClient.buscarPorCep("01001000")).thenThrow(new ServicoExternoIndisponivelException("ViaCep", null));
        mockMvc.perform(get("/enderecos/cep/01001000"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Serviço externo indisponível: ViaCep"));
    }
}
