package com.couto.chefe_api.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.couto.chefe_api.enums.TipoUsuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class TokenService {

    private static final String ISSUER = "chefe-api";
    private static final String FINALIDADE = "finalidade";
    private static final String ACESSO = "acesso";
    private static final String REGISTRO = "registro";
    private static final Duration DURACAO_ACESSO = Duration.ofHours(8);
    private static final Duration DURACAO_REGISTRO = Duration.ofMinutes(15);

    private final Algorithm algorithm;

    public TokenService(@Value("${jwt.key}") String secret) {
        this.algorithm = Algorithm.HMAC256(secret);
    }

    public String gerarToken(UsuarioAutenticado usuario) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(usuario.email())
                .withClaim("id", usuario.id().toString())
                .withClaim("tipo", usuario.tipo().name())
                .withClaim(FINALIDADE, ACESSO)
                .withExpiresAt(Instant.now().plus(DURACAO_ACESSO))
                .sign(algorithm);
    }

    public Optional<UsuarioAutenticado> validarToken(String token) {
        return verificar(token, ACESSO).flatMap(jwt -> {
            try {
                return Optional.of(new UsuarioAutenticado(
                        UUID.fromString(jwt.getClaim("id").asString()),
                        jwt.getSubject(),
                        TipoUsuario.valueOf(jwt.getClaim("tipo").asString())
                ));
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        });
    }

    public String gerarTokenRegistro(GoogleUser googleUser) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(googleUser.email())
                .withClaim("nome", googleUser.nome())
                .withClaim("foto", googleUser.foto())
                .withClaim(FINALIDADE, REGISTRO)
                .withExpiresAt(Instant.now().plus(DURACAO_REGISTRO))
                .sign(algorithm);
    }

    public Optional<GoogleUser> validarTokenRegistro(String token) {
        return verificar(token, REGISTRO).map(jwt -> new GoogleUser(
                jwt.getSubject(),
                jwt.getClaim("nome").asString(),
                jwt.getClaim("foto").asString()
        ));
    }

    private Optional<DecodedJWT> verificar(String token, String finalidade) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .withClaim(FINALIDADE, finalidade)
                    .withClaimPresence("sub")
                    .build();
            return Optional.of(verifier.verify(token));
        } catch (JWTVerificationException e) {
            return Optional.empty();
        }
    }
}
