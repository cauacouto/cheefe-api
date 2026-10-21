package com.couto.chefe_api.Security.bff;

import com.couto.chefe_api.Security.AuthCookies;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo do BFF com o Google simulado: troca do code, ID token e user info são beans falsos.
 */
@SpringBootTest
@ActiveProfiles("test")
class GoogleLoginFluxoCompletoIntegrationTest {

    private static final AtomicReference<String> NONCE = new AtomicReference<>();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void contaNovaVaiParaCadastroSemGuardarTokenDoGoogleEmMemoria() throws Exception {
        MvcResult inicio = mockMvc.perform(get("/oauth2/authorization/google")).andReturn();
        MultiValueMap<String, String> params = UriComponentsBuilder
                .fromUriString(inicio.getResponse().getHeader("Location")).build().getQueryParams();
        NONCE.set(URLDecoder.decode(params.getFirst("nonce"), StandardCharsets.UTF_8));

        MvcResult callback = mockMvc.perform(get("/login/oauth2/code/google")
                        .param("code", "abc")
                        .param("state", URLDecoder.decode(params.getFirst("state"), StandardCharsets.UTF_8))
                        .cookie(cookieDoFluxo(inicio)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/cadastro"))
                .andReturn();

        assertTrue(callback.getResponse().getHeaders("Set-Cookie").stream()
                .anyMatch(c -> c.startsWith(AuthCookies.REGISTRO_TOKEN + "=")));
        assertNull(callback.getRequest().getSession(false));
        assertNull(authorizedClientService.loadAuthorizedClient("google", "123"));
    }

    private static Cookie cookieDoFluxo(MvcResult inicio) {
        String header = inicio.getResponse().getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith(AuthCookies.OAUTH2_REQUEST + "="))
                .findFirst()
                .orElseThrow();
        return new Cookie(AuthCookies.OAUTH2_REQUEST, header.substring(header.indexOf('=') + 1, header.indexOf(';')));
    }

    @TestConfiguration
    static class GoogleFalso {

        @Bean
        OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokenResponseClientFalso() {
            return request -> OAuth2AccessTokenResponse.withToken("access-token-google")
                    .tokenType(OAuth2AccessToken.TokenType.BEARER)
                    .expiresIn(3600)
                    .scopes(Set.of("openid", "profile", "email"))
                    .additionalParameters(Map.of("id_token", "id-token-falso"))
                    .build();
        }

        @Bean
        JwtDecoderFactory<ClientRegistration> idTokenDecoderFalso() {
            return registration -> token -> Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .claim("sub", "123")
                    .claim("iss", "https://accounts.google.com")
                    .claim("aud", List.of(registration.getClientId()))
                    .claim("nonce", NONCE.get())
                    .claim("email", "Nova@Gmail.com")
                    .claim("email_verified", true)
                    .claim("name", "Nova")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();
        }

        @Bean
        OAuth2UserService<OidcUserRequest, OidcUser> oidcUserServiceFalso() {
            return request -> new DefaultOidcUser(List.of(), request.getIdToken());
        }
    }
}
