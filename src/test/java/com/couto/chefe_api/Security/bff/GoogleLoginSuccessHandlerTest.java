package com.couto.chefe_api.Security.bff;

import com.couto.chefe_api.Dtos.LoginResult;
import com.couto.chefe_api.Excepitons.ContaInativaException;
import com.couto.chefe_api.Security.AuthCookies;
import com.couto.chefe_api.Security.GoogleUser;
import com.couto.chefe_api.Security.LoginService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class GoogleLoginSuccessHandlerTest {

    private static final String FRONT = "http://localhost:5173";

    private final LoginService loginService = mock(LoginService.class);
    private final GoogleLoginSuccessHandler handler =
            new GoogleLoginSuccessHandler(loginService, new AuthCookies(false), FRONT);

    @Test
    void contaExistenteGravaAccessTokenERedirecionaParaRaiz() throws Exception {
        when(loginService.loginGoogle(any())).thenReturn(new LoginResult("jwt", null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacao(true));

        assertEquals(FRONT + "/", response.getRedirectedUrl());
        assertTrue(response.getHeaders("Set-Cookie").stream().anyMatch(c -> c.startsWith("accessToken=jwt")));
    }

    @Test
    void contaNovaGravaRegistroERedirecionaParaCadastro() throws Exception {
        when(loginService.loginGoogle(any())).thenReturn(new LoginResult(null, "reg"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacao(true));

        assertEquals(FRONT + "/cadastro", response.getRedirectedUrl());
        assertTrue(response.getHeaders("Set-Cookie").stream().anyMatch(c -> c.startsWith("registroToken=reg")));
    }

    @Test
    void emailEhNormalizado() throws Exception {
        when(loginService.loginGoogle(any())).thenReturn(new LoginResult("jwt", null));

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), autenticacao(true));

        verify(loginService).loginGoogle(new GoogleUser("ana@gmail.com", "Ana", "https://foto"));
    }

    @Test
    void emailNaoVerificadoRedirecionaParaErroSemCookies() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacao(false));

        assertEquals(FRONT + "/login?erro=google", response.getRedirectedUrl());
        assertTrue(response.getHeaders("Set-Cookie").isEmpty());
        verify(loginService, never()).loginGoogle(any());
    }

    @Test
    void contaApagadaRedirecionaParaErroSemCookies() throws Exception {
        when(loginService.loginGoogle(any())).thenThrow(new ContaInativaException());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacao(true));

        assertEquals(FRONT + "/login?erro=conta-inativa", response.getRedirectedUrl());
        assertTrue(response.getHeaders("Set-Cookie").isEmpty());
    }

    @Test
    void erroInesperadoNoLoginRedirecionaParaErroERegistraNoLog(CapturedOutput output) throws Exception {
        when(loginService.loginGoogle(any())).thenThrow(new IllegalStateException("banco fora do ar"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacao(true));

        assertEquals(FRONT + "/login?erro=google", response.getRedirectedUrl());
        assertTrue(response.getHeaders("Set-Cookie").isEmpty());
        assertTrue(output.getOut().contains("banco fora do ar"));
    }

    @Test
    void falhaRedirecionaParaErro() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new GoogleLoginFailureHandler(FRONT)
                .onAuthenticationFailure(new MockHttpServletRequest(), response, new BadCredentialsException("x"));

        assertEquals(FRONT + "/login?erro=google", response.getRedirectedUrl());
    }

    private static OAuth2AuthenticationToken autenticacao(boolean emailVerificado) {
        OidcIdToken idToken = OidcIdToken.withTokenValue("t")
                .claim("sub", "123")
                .claim("email", "Ana@Gmail.com")
                .claim("email_verified", emailVerificado)
                .claim("name", "Ana")
                .claim("picture", "https://foto")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), idToken), List.of(), "google");
    }
}
