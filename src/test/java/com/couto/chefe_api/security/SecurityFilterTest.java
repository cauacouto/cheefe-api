package com.couto.chefe_api.security;

import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SecurityFilterTest {

    private final TokenService tokenService = new TokenService("teste");
    private final ContaBloqueioVerificador bloqueio = mock(ContaBloqueioVerificador.class);
    private final SecurityFilter filter = new SecurityFilter(tokenService, new AuthCookies(false), new ModeracaoRegistry(""), bloqueio);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cookieValidoAutentica() throws Exception {
        UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.randomUUID(), "ana@gmail.com", TipoUsuario.CLIENTE);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(usuario)));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(usuario, authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_CLIENTE")));
        assertNotNull(chain.getRequest());
    }

    @Test
    void cookieInvalidoNaoAutenticaNemLanca() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookies.ACCESS_TOKEN, "lixo"));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }

    @Test
    void semCookieNaoAutentica() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }

    @Test
    void contaBloqueadaNaoAutentica() throws Exception {
        UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.randomUUID(), "ana@gmail.com", TipoUsuario.CLIENTE);
        when(bloqueio.bloqueada(usuario)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(usuario)));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }

    @Test
    void semCookieNaoConsultaBloqueio() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        verifyNoInteractions(bloqueio);
    }

    @Test
    void moderadorNaoConsultaOBanco() throws Exception {
        ModeracaoRegistry registry = new ModeracaoRegistry("mod@x.com");
        UsuarioAutenticado mod = new UsuarioAutenticado(registry.idDe("mod@x.com"), "mod@x.com", TipoUsuario.MODERADOR);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        ChefeRepository chefeRepository = mock(ChefeRepository.class);
        SecurityFilter real = new SecurityFilter(tokenService, new AuthCookies(false),
                registry, new ContaBloqueioVerificador(usuarioRepository, chefeRepository));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookies.ACCESS_TOKEN, tokenService.gerarToken(mod)));

        real.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(usuarioRepository, chefeRepository);
    }
}
