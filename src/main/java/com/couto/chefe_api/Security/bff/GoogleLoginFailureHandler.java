package com.couto.chefe_api.Security.bff;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GoogleLoginFailureHandler implements AuthenticationFailureHandler {

    static final String PATH_ERRO = "/login?erro=google";

    private final String frontUrl;

    public GoogleLoginFailureHandler(@Value("${app.front-url}") String frontUrl) {
        this.frontUrl = frontUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        response.sendRedirect(frontUrl + PATH_ERRO);
    }
}
