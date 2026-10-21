package com.couto.chefe_api.Dtos;

public record LoginResult(
        String accessToken,
        String registroToken
) {
}
