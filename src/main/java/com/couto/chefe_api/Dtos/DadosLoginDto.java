package com.couto.chefe_api.Dtos;

import lombok.Getter;
import lombok.Setter;


public record DadosLoginDto (
        String email,
        String passaword
) {
}
