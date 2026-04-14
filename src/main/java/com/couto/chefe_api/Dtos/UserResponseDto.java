package com.couto.chefe_api.Dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class UserResponseDto {
    private UUID id;
    private String nome;
    private String email;
    private List<EnderecoResponseDto> endereco;
    private String telefone;
}

