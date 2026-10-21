package com.couto.chefe_api.Security;

import com.couto.chefe_api.enums.TipoUsuario;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    private final TokenService tokenService = new TokenService("teste");
    private final UsuarioAutenticado usuario =
            new UsuarioAutenticado(UUID.randomUUID(), "ana@gmail.com", TipoUsuario.CHEFE);
    private final GoogleUser googleUser = new GoogleUser("ana@gmail.com", "Ana", "https://foto");

    @Test
    void tokenDeAcessoFazIdaEVolta() {
        Optional<UsuarioAutenticado> resultado = tokenService.validarToken(tokenService.gerarToken(usuario));

        assertEquals(Optional.of(usuario), resultado);
        assertTrue(resultado.get().getAuthorities().contains(new SimpleGrantedAuthority("ROLE_CHEFE")));
    }

    @Test
    void tokenDeRegistroFazIdaEVolta() {
        String token = tokenService.gerarTokenRegistro(googleUser);

        assertEquals(Optional.of(googleUser), tokenService.validarTokenRegistro(token));
    }

    @Test
    void tokenDeRegistroSemFoto() {
        GoogleUser semFoto = new GoogleUser("ana@gmail.com", "Ana", null);

        GoogleUser resultado = tokenService.validarTokenRegistro(tokenService.gerarTokenRegistro(semFoto)).orElseThrow();

        assertNull(resultado.foto());
        assertEquals(semFoto, resultado);
    }

    @Test
    void tokenDeRegistroNaoServeComoAcesso() {
        assertEquals(Optional.empty(), tokenService.validarToken(tokenService.gerarTokenRegistro(googleUser)));
    }

    @Test
    void tokenDeAcessoNaoServeComoRegistro() {
        assertEquals(Optional.empty(), tokenService.validarTokenRegistro(tokenService.gerarToken(usuario)));
    }

    @Test
    void tokenAdulteradoEhRejeitado() {
        String token = tokenService.gerarToken(usuario);
        char ultimo = token.charAt(token.length() - 1);
        String adulterado = token.substring(0, token.length() - 1) + (ultimo == 'A' ? 'B' : 'A');

        assertEquals(Optional.empty(), tokenService.validarToken(adulterado));
    }

    @Test
    void tokenDeOutraChaveEhRejeitado() {
        String token = new TokenService("outra").gerarToken(usuario);

        assertEquals(Optional.empty(), tokenService.validarToken(token));
    }

    @Test
    void tokenVazioOuNuloEhRejeitado() {
        assertEquals(Optional.empty(), tokenService.validarToken(""));
        assertEquals(Optional.empty(), tokenService.validarToken(null));
        assertEquals(Optional.empty(), tokenService.validarTokenRegistro(null));
    }
}
