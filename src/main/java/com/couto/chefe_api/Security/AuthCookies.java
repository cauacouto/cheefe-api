package com.couto.chefe_api.Security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Component
public class AuthCookies {

    public static final String ACCESS_TOKEN = "accessToken";
    public static final String REGISTRO_TOKEN = "registroToken";
    public static final String OAUTH2_REQUEST = "oauth2_auth_request";

    public static final String PATH_RAIZ = "/";
    public static final String PATH_REGISTRO = "/auth/register";

    private final boolean secure;

    public AuthCookies(@Value("${auth.cookie.secure:true}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie accessToken(String token) {
        return criar(ACCESS_TOKEN, token, PATH_RAIZ, Duration.ofHours(8));
    }

    public ResponseCookie registroToken(String token) {
        return criar(REGISTRO_TOKEN, token, PATH_REGISTRO, Duration.ofMinutes(15));
    }

    /**
     * Path=/ de propósito: o cookie nasce em {@code /oauth2/authorization/google} e é lido no callback
     * {@code /login/oauth2/code/google}. O navegador só envia o cookie quando o path dele é prefixo da URL,
     * e o único prefixo comum aos dois é "/". Um path mais estreito exigiria mover o redirect URI
     * (e o cadastro no Google Console). O custo (~3 KB por requisição) dura no máximo os 180s do Max-Age,
     * e o cookie é expirado assim que o callback o consome.
     */
    public ResponseCookie oauth2Request(String valor) {
        return criar(OAUTH2_REQUEST, valor, PATH_RAIZ, Duration.ofSeconds(180));
    }

    public ResponseCookie expirar(String nome, String path) {
        return criar(nome, "", path, Duration.ZERO);
    }

    public void adicionar(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public Optional<String> ler(HttpServletRequest request, String nome) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> nome.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst();
    }

    private ResponseCookie criar(String nome, String valor, String path, Duration maxAge) {
        return ResponseCookie.from(nome, valor)
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
    }
}
