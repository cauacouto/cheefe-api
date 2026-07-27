package com.couto.chefe_api.Security.otp;

import com.couto.chefe_api.Dtos.otpData;
import com.couto.chefe_api.Excepitons.OtpException;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private static final String OTP_PREFIX = "otp:";
    private static final String ATTEMPT_PREFIX = "otp:attempt:";
    private final ObjectMapper objectMapper;


    public String gerarCodigo() {
        SecureRandom secureRandom = new SecureRandom();
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    public void salvarCodigo(String otpId,String email, String codigo ) {

        String hash = passwordEncoder.encode(codigo);

        otpData data = new otpData(
                email,
                hash
        );
        try {
            redisTemplate.opsForValue()
                    .set(
                            OTP_PREFIX + otpId,
                            objectMapper.writeValueAsString(data),
                            Duration.ofMinutes(5)
                    );

    } catch (OtpException e) {
            throw new RuntimeException(e);
        }

    }



    public Long verificarTentativas(String otpId){


        String key = ATTEMPT_PREFIX + otpId;

        Long tentativas = redisTemplate.opsForValue()
                .increment(key);

        if (tentativas == 1) {
            redisTemplate.expire(
                    key,
                    Duration.ofMinutes(5)
            );
        }

        if (tentativas > 5){
            redisTemplate.delete(OTP_PREFIX + otpId);
            redisTemplate.delete(key);

            throw new RuntimeException("Muitas tentativas");
        }

        return tentativas;
    }


    public otpData validarCodigo(String otpId, String codigo) {

        String json = redisTemplate.opsForValue()
                .get(OTP_PREFIX + otpId);
        if (json == null) {
            throw new RuntimeException("otp expirado");
        }

        try {


        otpData data = objectMapper.readValue(
                json,
                otpData.class
        );


        if (!passwordEncoder.matches(codigo, data.codigoHash())) {
            verificarTentativas(otpId);
            throw new RuntimeException("OTP INVALIDO");
        }
        redisTemplate.delete(OTP_PREFIX + otpId);
        redisTemplate.delete(ATTEMPT_PREFIX + otpId);

        return data;

        }catch (OtpException ex){
            throw  new RuntimeException("Erro ao ler otp", ex);
        }
    }







}
