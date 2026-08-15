package com.couto.chefe_api.Config.Infra;

import com.couto.chefe_api.Dtos.ViaCepDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ViaCepClient {

    private final WebClient viaCepClient;


    public ViaCepClient(@Qualifier("viaCep") WebClient viaCepClient) {
        this.viaCepClient = viaCepClient;
    }

    public Mono<ViaCepDto> buscarPorCep(String cep){
        return viaCepClient
                .get()
                .uri("/ws/{cep}/json/", cep)
                .retrieve()
                .bodyToMono(ViaCepDto.class);
    }
}
