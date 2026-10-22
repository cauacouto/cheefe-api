package com.couto.chefe_api.dto;

public record RegisterResult(
        String accessToken,
        Object corpo
) {
}
