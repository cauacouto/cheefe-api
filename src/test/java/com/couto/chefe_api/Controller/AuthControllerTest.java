package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.DadosLoginDto;
import com.couto.chefe_api.Dtos.LoginResponseDto;
import com.couto.chefe_api.Dtos.LoginResult;
import com.couto.chefe_api.Security.LoginService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthControllerTest {

    @Test
    void loginStoresTokenInHttpOnlyCookieWithoutExposingItInResponseBody() {
        LoginResponseDto loginResponse = new LoginResponseDto(false, null);
        LoginService loginService = loginServiceReturning(new LoginResult("jwt-value", loginResponse));
        AuthController controller = new AuthController(loginService);
        DadosLoginDto login = new DadosLoginDto("otp-id", "123456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.login(login, response);

        assertEquals(loginResponse, result.getBody());
        assertFalse(result.getBody().cadastroPedende());
        assertNull(result.getBody().email());
        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.contains("accessToken=jwt-value"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("Max-Age=28800"));
        assertTrue(cookie.contains("SameSite=Lax"));
    }

    @Test
    void logoutExpiresAccessTokenCookie() {
        AuthController controller = new AuthController(loginServiceReturning(null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(response);

        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.contains("accessToken="));
        assertTrue(cookie.contains("Max-Age=0"));
        assertTrue(cookie.contains("HttpOnly"));
    }

    private LoginService loginServiceReturning(LoginResult expectedResult) {
        return new LoginService(null, null, null, null, null, null, null, null) {
            @Override
            public LoginResult login(DadosLoginDto dados) {
                return expectedResult;
            }
        };
    }
}
