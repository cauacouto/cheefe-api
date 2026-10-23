package com.couto.chefe_api;

import com.couto.chefe_api.enums.CategoriaConteudo;
import com.couto.chefe_api.service.FiltroConteudo;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;

/**
 * Termos fictícios: os testes não dependem da lista de produção. Uma classe só (via {@code @Import}):
 * os testes que a usam dividem o mesmo contexto do Spring.
 */
@TestConfiguration
public class FiltroDeTeste {

    @Bean
    @Primary
    FiltroConteudo filtroDeTeste() {
        return new FiltroConteudo(Map.of(
                CategoriaConteudo.OFENSA, List.of("palavrao"),
                CategoriaConteudo.ODIO, List.of("odiento"),
                CategoriaConteudo.AMEACA, List.of("vou te pegar")));
    }
}
