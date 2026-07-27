package com.couto.chefe_api.Dtos;

public record EmailRequest(
        String from,
        String[] to,
        String subject,
        String html
) {
}
