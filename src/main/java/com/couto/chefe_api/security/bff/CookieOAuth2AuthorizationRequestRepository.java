package com.couto.chefe_api.security.bff;

import com.couto.chefe_api.security.AuthCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.Base64;

/**
 * Guarda o authorization request do OAuth2 num cookie cifrado (AES-256-GCM) em vez da HttpSession,
 * para a API continuar stateless. A autenticação do GCM é verificada antes da desserialização.
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String SALT = "6368656665617574";
    private static final ObjectInputFilter FILTRO =
            ObjectInputFilter.Config.createFilter("org.springframework.security.**;java.**;!*");

    private final AuthCookies authCookies;
    private final BytesEncryptor encryptor;

    public CookieOAuth2AuthorizationRequestRepository(AuthCookies authCookies, @Value("${jwt.key}") String chave) {
        this.authCookies = authCookies;
        this.encryptor = Encryptors.stronger(chave, SALT);
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return authCookies.ler(request, AuthCookies.OAUTH2_REQUEST)
                .map(this::decodificar)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            expirar(response);
            return;
        }
        authCookies.adicionar(response, authCookies.oauth2Request(codificar(authorizationRequest)));
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        expirar(response);
        return authorizationRequest;
    }

    private void expirar(HttpServletResponse response) {
        authCookies.adicionar(response, authCookies.expirar(AuthCookies.OAUTH2_REQUEST, AuthCookies.PATH_RAIZ));
    }

    private String codificar(OAuth2AuthorizationRequest authorizationRequest) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(authorizationRequest);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(encryptor.encrypt(bytes.toByteArray()));
    }

    private OAuth2AuthorizationRequest decodificar(String valor) {
        try {
            byte[] decifrado = encryptor.decrypt(Base64.getUrlDecoder().decode(valor));
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(decifrado))) {
                in.setObjectInputFilter(FILTRO);
                return (OAuth2AuthorizationRequest) in.readObject();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
