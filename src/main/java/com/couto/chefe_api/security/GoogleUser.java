package com.couto.chefe_api.security;

public record GoogleUser(
        String email,
        String nome,
        String foto
) {
}
