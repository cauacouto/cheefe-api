package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.EnderecoResponse;
import com.couto.chefe_api.Services.ViaCepService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/enderecos")
@RequiredArgsConstructor
public class EnderecoController {

    private final ViaCepService viaCepService;

    @GetMapping("/cep/{cep}")
    public Mono<EnderecoResponse> buscarPorCep(@PathVariable String cep) {
        return viaCepService.buscarPorCep(cep);
    }
}
