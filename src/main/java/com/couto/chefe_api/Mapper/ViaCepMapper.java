package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.EnderecoResponse;
import com.couto.chefe_api.Dtos.ViaCepDto;
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
