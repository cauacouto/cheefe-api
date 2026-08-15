package com.couto.chefe_api.Services;

import com.couto.chefe_api.Config.Infra.ViaCepClient;
import com.couto.chefe_api.Dtos.EnderecoResponse;
import com.couto.chefe_api.Mapper.ViaCepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ViaCepService {

    private final ViaCepClient viaCepClient;
    private final ViaCepMapper viaCepMapper;

    public Mono<EnderecoResponse> buscarPorCep(String cep) {
        return viaCepClient.buscarPorCep(cep)
                .map(viaCepMapper::toResponse);
    }
}
