package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.RegisterRequestDto;
import com.couto.chefe_api.Dtos.RegisterResult;
import com.couto.chefe_api.Dtos.RegistroPendenteDto;
import com.couto.chefe_api.Dtos.SessaoDto;
import com.couto.chefe_api.Dtos.UserResponseDto;
import com.couto.chefe_api.Security.AuthCookies;
import com.couto.chefe_api.Security.LoginService;
import com.couto.chefe_api.Security.UsuarioAutenticado;
import com.couto.chefe_api.enums.TipoUsuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final LoginService loginService = mock(LoginService.class);
    private final AuthController controller = new AuthController(loginService, new AuthCookies(false));

    @Test
    void getRegisterDevolveDadosPendentes() {
        RegistroPendenteDto dados = new RegistroPendenteDto("ana@gmail.com", "Ana", "https://foto");
        when(loginService.dadosRegistro("tok")).thenReturn(dados);

        assertEquals(dados, controller.dadosRegistro("tok").getBody());
    }

    @Test
    void postRegisterGravaAccessTokenEExpiraRegistro() {
        RegisterRequestDto dto = new RegisterRequestDto();
        UserResponseDto corpo = new UserResponseDto();
        when(loginService.register(dto, null, "tok")).thenReturn(new RegisterResult("jwt", corpo));
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.register(dto, null, "tok", response);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(corpo, result.getBody());
        List<String> cookies = response.getHeaders("Set-Cookie");
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("accessToken=jwt")));
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("registroToken=") && c.contains("Max-Age=0")));
    }

    @Test
    void meDevolveSessao() {
        UUID id = UUID.randomUUID();

        var result = controller.me(new UsuarioAutenticado(id, "ana@gmail.com", TipoUsuario.CHEFE));

        assertEquals(new SessaoDto(id, "ana@gmail.com", TipoUsuario.CHEFE), result.getBody());
    }

    @Test
    void logoutExpiraOsDoisCookies() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(response);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("accessToken=") && c.contains("Max-Age=0") && c.contains("HttpOnly")));
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("registroToken=") && c.contains("Path=/auth/register") && c.contains("Max-Age=0")));
    }
}
