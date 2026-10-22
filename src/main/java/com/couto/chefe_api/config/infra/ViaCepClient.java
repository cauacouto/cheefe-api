package com.couto.chefe_api.config.infra;

import com.couto.chefe_api.dto.ViaCepDto;
import com.couto.chefe_api.exception.ServicoExternoIndisponivelException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ViaCepClient {

    private final RestClient viaCepClient;

    public ViaCepClient(@Qualifier("viaCep") RestClient viaCepClient) {
        this.viaCepClient = viaCepClient;
    }

    /** cep só com os 8 dígitos (o ViaCepService normaliza e valida antes). */
    public ViaCepDto buscarPorCep(String cep) {
        try {
            return viaCepClient
                    .get()
                    .uri("/ws/{cep}/json/", cep)
                    .retrieve()
                    .body(ViaCepDto.class);
        } catch (RestClientException e) {
            throw new ServicoExternoIndisponivelException("ViaCep", e);
        }
    }
}
