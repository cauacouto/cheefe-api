package com.couto.chefe_api.Config.Infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ViaCepWebClientConfig {


    @Bean
    public WebClient viaCep(){
        return WebClient.builder()
                .baseUrl("https://viacep.com.br")
                .build();

    }

}
