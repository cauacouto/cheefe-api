package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Security.AuthCookies;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthControllerTest {

    private final AuthController controller = new AuthController(null, new AuthCookies(false));

    @Test
    void logoutExpiraOsDoisCookies() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(response);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("accessToken=") && c.contains("Max-Age=0") && c.contains("HttpOnly")));
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("registroToken=") && c.contains("Path=/auth/register") && c.contains("Max-Age=0")));
    }
}
