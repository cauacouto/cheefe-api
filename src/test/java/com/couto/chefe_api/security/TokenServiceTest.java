package com.couto.chefe_api.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.couto.chefe_api.enums.TipoUsuario;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
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
        // altera um caractere do payload (o último caractere da assinatura tem bits de padding descartados)
        int meioDoPayload = (token.indexOf('.') + token.lastIndexOf('.')) / 2;
        char original = token.charAt(meioDoPayload);
        String adulterado = token.substring(0, meioDoPayload) + (original == 'A' ? 'B' : 'A') + token.substring(meioDoPayload + 1);

        assertEquals(Optional.empty(), tokenService.validarToken(adulterado));
    }

    @Test
    void tokenDeOutraChaveEhRejeitado() {
        String token = new TokenService("outra").gerarToken(usuario);

        assertEquals(Optional.empty(), tokenService.validarToken(token));
    }

    @Test
    void tokenDeAcessoExpiradoEhRejeitado() {
        String expirado = JWT.create()
                .withIssuer("chefe-api")
                .withSubject(usuario.email())
                .withClaim("id", usuario.id().toString())
                .withClaim("tipo", usuario.tipo().name())
                .withClaim("finalidade", "acesso")
                .withExpiresAt(Instant.now().minusSeconds(60))
                .sign(Algorithm.HMAC256("teste"));

        assertEquals(Optional.empty(), tokenService.validarToken(expirado));
    }

    @Test
    void tokenVazioOuNuloEhRejeitado() {
        assertEquals(Optional.empty(), tokenService.validarToken(""));
        assertEquals(Optional.empty(), tokenService.validarToken(null));
        assertEquals(Optional.empty(), tokenService.validarTokenRegistro(null));
    }
}
