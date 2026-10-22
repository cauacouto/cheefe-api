package com.couto.chefe_api.mapper;

import com.couto.chefe_api.dto.EnderecoResponse;
import com.couto.chefe_api.dto.ViaCepDto;
import org.springframework.stereotype.Component;

@Component
public class ViaCepMapper {

    public EnderecoResponse toResponse(ViaCepDto endereco) {
        return new EnderecoResponse(
                endereco.cep(),
                endereco.logradouro(),
                endereco.bairro(),
                endereco.localidade(),
                endereco.uf()
        );
    }
}
