package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.EnderecoResponse;
import com.couto.chefe_api.Dtos.ViaCepDto;
import com.couto.chefe_api.domin.EnderecoModel;
import org.springframework.stereotype.Component;

@Component
public class AclMapper {


    public EnderecoModel mapper(ViaCepDto cepDto){
        EnderecoModel endereco = new EnderecoModel();

        endereco.setBairro(cepDto.bairro());
        endereco.setCep(cepDto.cep());
        endereco.setCidade(cepDto.localidade());
        endereco.setEstado(cepDto.uf());
        endereco.setRua(cepDto.logradouro());
        return endereco;
    }

    public EnderecoResponse toResponse(EnderecoModel endereco) {
        return new EnderecoResponse(
                endereco.getCep(),
                endereco.getRua(),
                endereco.getBairro(),
                endereco.getCidade(),
                endereco.getEstado()
        );
    }
}
