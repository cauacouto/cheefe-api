package com.couto.chefe_api.Security.bff;

import com.couto.chefe_api.Security.AuthCookies;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookieOAuth2AuthorizationRequestRepositoryTest {

    private final AuthCookies authCookies = new AuthCookies(false);
    private final CookieOAuth2AuthorizationRequestRepository repository =
            new CookieOAuth2AuthorizationRequestRepository(authCookies, "teste");

    private final OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .clientId("cid")
            .redirectUri("http://localhost/login/oauth2/code/google")
            .state("st")
            .attributes(a -> a.put("registration_id", "google"))
            .build();

    @Test
    void salvaECarrega() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(authorizationRequest, new MockHttpServletRequest(), response);

        String header = response.getHeader("Set-Cookie");
        assertTrue(header.startsWith("oauth2_auth_request="));
        assertTrue(header.contains("Max-Age=180"));

        OAuth2AuthorizationRequest carregado = repository.loadAuthorizationRequest(requestCom(valorDoCookie(response)));
        assertEquals("st", carregado.getState());
        assertEquals("cid", carregado.getClientId());
        assertEquals("google", carregado.getAttribute("registration_id"));
    }

    @Test
    void removeDevolveEExpira() {
        MockHttpServletResponse salvo = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(authorizationRequest, new MockHttpServletRequest(), salvo);
        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2AuthorizationRequest removido = repository.removeAuthorizationRequest(requestCom(valorDoCookie(salvo)), response);

        assertEquals("st", removido.getState());
        String header = response.getHeader("Set-Cookie");
        assertTrue(header.startsWith("oauth2_auth_request="));
        assertTrue(header.contains("Max-Age=0"));
    }

    @Test
    void cookieAdulteradoDevolveNulo() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(authorizationRequest, new MockHttpServletRequest(), response);
        String valor = valorDoCookie(response);
        int meio = valor.length() / 2;
        String adulterado = valor.substring(0, meio) + (valor.charAt(meio) == 'A' ? 'B' : 'A') + valor.substring(meio + 1);

        assertNull(repository.loadAuthorizationRequest(requestCom(adulterado)));
    }

    @Test
    void cookieDeOutraChaveDevolveNulo() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CookieOAuth2AuthorizationRequestRepository(authCookies, "outra")
                .saveAuthorizationRequest(authorizationRequest, new MockHttpServletRequest(), response);

        assertNull(repository.loadAuthorizationRequest(requestCom(valorDoCookie(response))));
    }

    @Test
    void semCookieDevolveNulo() {
        assertNull(repository.loadAuthorizationRequest(new MockHttpServletRequest()));
    }

    @Test
    void salvarNuloExpira() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(null, new MockHttpServletRequest(), response);

        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
    }

    private static String valorDoCookie(MockHttpServletResponse response) {
        String header = response.getHeader("Set-Cookie");
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    private static MockHttpServletRequest requestCom(String valor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookies.OAUTH2_REQUEST, valor));
        return request;
    }
}
