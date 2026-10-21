package com.couto.chefe_api.Controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthControllerTest {

    @Test
    void logoutExpiresAccessTokenCookie() {
        AuthController controller = new AuthController(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(response);

        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.contains("accessToken="));
        assertTrue(cookie.contains("Max-Age=0"));
        assertTrue(cookie.contains("HttpOnly"));
    }
}
