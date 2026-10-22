package com.couto.chefe_api.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthCookiesTest {

    @Test
    void accessTokenTemAtributosCorretos() {
        String cookie = new AuthCookies(true).accessToken("v").toString();

        assertTrue(cookie.contains("accessToken=v"));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("Max-Age=28800"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Secure"));
    }

    @Test
    void registroTokenTemPathDeCadastro() {
        String cookie = new AuthCookies(true).registroToken("v").toString();

        assertTrue(cookie.contains("registroToken=v"));
        assertTrue(cookie.contains("Path=/auth/register"));
        assertTrue(cookie.contains("Max-Age=900"));
    }

    @Test
    void semSecureQuandoDesligado() {
        String cookie = new AuthCookies(false).accessToken("v").toString();

        assertFalse(cookie.contains("Secure"));
    }

    @Test
    void lerIgnoraCookieEmBranco() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookies.ACCESS_TOKEN, ""));

        assertEquals(Optional.empty(), new AuthCookies(false).ler(request, AuthCookies.ACCESS_TOKEN));
    }
}
