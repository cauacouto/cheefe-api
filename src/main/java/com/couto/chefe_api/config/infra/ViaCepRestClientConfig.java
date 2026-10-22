package com.couto.chefe_api.config.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ViaCepRestClientConfig {

    /** Timeouts curtos: o formulário de endereço não pode ficar preso esperando o ViaCep. */
    @Bean
    public RestClient viaCep() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(2));
        fabrica.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .baseUrl("https://viacep.com.br")
                .requestFactory(fabrica)
                .build();
    }
}
