package com.couto.chefe_api.Dtos;

import java.util.List;

public record UserRequestDto(String nome, String email, String password, String telefone, List<EnderecoRequestDto> endereco
                             ) {
}
