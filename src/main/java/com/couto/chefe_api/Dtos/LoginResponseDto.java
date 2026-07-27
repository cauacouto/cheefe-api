package com.couto.chefe_api.Dtos;

public record LoginResponseDto (
        Boolean cadastroPedende,
        String email,
        String token
){
}
