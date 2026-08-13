package com.couto.chefe_api.Security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.couto.chefe_api.User.UserModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class TokenService {

    @Value("${jwt.key}")
    private String secret;

    public String gerarToken(UserModel user){

        try {

            Algorithm algorithm = Algorithm.HMAC256(secret);
            String token = JWT.create()
                    .withIssuer("chefe-api")
                    .withSubject(user.getEmail())
                    .withExpiresAt(experiraToken())
                    .sign(algorithm);
            return token;

        }catch (JWTVerificationException e){
            throw new RuntimeException("erro ao gerar token",e);
        }
    }

    public String validarToken(String token){

        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("chefe-api")
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception){
            return "";
        }

    }

    private Instant experiraToken (){
return LocalDateTime.now().plusHours(8).toInstant(ZoneOffset.of("-03:00"));
    }
}
