package com.couto.chefe_api.Security.bff;

import com.couto.chefe_api.Dtos.LoginResult;
import com.couto.chefe_api.Excepitons.ContaInativaException;
import com.couto.chefe_api.Security.AuthCookies;
import com.couto.chefe_api.Security.GoogleUser;
import com.couto.chefe_api.Security.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

@Slf4j
@Component
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final LoginService loginService;
    private final AuthCookies authCookies;
    private final String frontUrl;

    public GoogleLoginSuccessHandler(LoginService loginService, AuthCookies authCookies,
                                     @Value("${app.front-url}") String frontUrl) {
        this.loginService = loginService;
        this.authCookies = authCookies;
        this.frontUrl = GoogleLoginFailureHandler.semBarraFinal(frontUrl);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)
                || !Boolean.TRUE.equals(oidcUser.getEmailVerified())
                || oidcUser.getEmail() == null) {
            response.sendRedirect(frontUrl + GoogleLoginFailureHandler.PATH_ERRO);
            return;
        }

        GoogleUser googleUser = new GoogleUser(
                oidcUser.getEmail().trim().toLowerCase(Locale.ROOT),
                oidcUser.getFullName(),
                oidcUser.getPicture()
        );
        LoginResult result;
        try {
            result = loginService.loginGoogle(googleUser);
        } catch (ContaInativaException e) {
            response.sendRedirect(frontUrl + "/login?erro=conta-inativa");
            return;
        } catch (RuntimeException e) {
            // ex.: banco fora do ar; o navegador está no meio do redirect, então volta ao front em vez de um JSON 500
            log.error("Erro inesperado no login com Google", e);
            response.sendRedirect(frontUrl + GoogleLoginFailureHandler.PATH_ERRO);
            return;
        }

        if (result.accessToken() != null) {
            authCookies.adicionar(response, authCookies.accessToken(result.accessToken()));
            response.sendRedirect(frontUrl + "/");
        } else {
            authCookies.adicionar(response, authCookies.registroToken(result.registroToken()));
            response.sendRedirect(frontUrl + "/cadastro");
        }
    }
}
