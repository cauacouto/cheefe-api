package com.couto.chefe_api.Security.bff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GoogleLoginFailureHandlerTest {

    private static final String FRONT = "http://localhost:5173";

    @Test
    void falhaDoFluxoRegistraOMotivoNoLog(CapturedOutput output) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        var excecao = new OAuth2AuthenticationException(new OAuth2Error("invalid_state_parameter"), "state divergente no callback");

        new GoogleLoginFailureHandler(FRONT).onAuthenticationFailure(new MockHttpServletRequest(), response, excecao);

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONT + "/login?erro=google");
        assertThat(output).contains("WARN").contains("state divergente no callback");
    }

    @Test
    void frontUrlComBarraFinalNaoGeraBarraDupla() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new GoogleLoginFailureHandler(FRONT + "/")
                .onAuthenticationFailure(new MockHttpServletRequest(), response, new OAuth2AuthenticationException("x"));

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONT + "/login?erro=google");
    }
}
