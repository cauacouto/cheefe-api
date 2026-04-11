package com.couto.chefe_api.Dtos;

import java.util.List;
import java.util.UUID;

public record UserResponseDto(UUID id, String nome, String email, List<EnderecoResponseDto> enderoco, String telefone) {
}
