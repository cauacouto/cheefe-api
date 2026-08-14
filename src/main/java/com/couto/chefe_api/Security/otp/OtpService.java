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
    private static final String EMAIL_PREFIX = "otp:email:";
    private final ObjectMapper objectMapper;


    public String gerarCodigo() {

        SecureRandom secureRandom = new SecureRandom();
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    public void salvarCodigo(String otpId,String email, String codigo ) {

        String hash = passwordEncoder.encode(codigo);

        String emailKey = emailKey(email);

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

            String otpAnterior = redisTemplate.opsForValue().getAndSet(emailKey, otpId);
            redisTemplate.expire(emailKey, Duration.ofMinutes(5));

            if (otpAnterior != null && !otpAnterior.equals(otpId)) {
                redisTemplate.delete(OTP_PREFIX + otpAnterior);
                redisTemplate.delete(ATTEMPT_PREFIX + otpAnterior);
            }

    } catch (OtpException e) {
            throw new RuntimeException(e);
        }

    }



    public Long verificarTentativas(String otpId){


        String otpKey = OTP_PREFIX + otpId;
        String attemptKey = ATTEMPT_PREFIX + otpId;

        Long tentativas = redisTemplate.opsForValue().increment(attemptKey);

          if (tentativas == 1){
              redisTemplate.expire(attemptKey,Duration.ofMinutes(5));
          }

        if (tentativas > 5){
            redisTemplate.delete(otpKey);
            redisTemplate.delete(attemptKey);

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

        String otpAtivo = redisTemplate.opsForValue().get(emailKey(data.email()));
        if (!otpId.equals(otpAtivo)) {
            throw new RuntimeException("OTP substituído");
        }


        if (!passwordEncoder.matches(codigo, data.codigoHash())) {
            verificarTentativas(otpId);
            throw new RuntimeException("OTP INVALIDO");
        }
        redisTemplate.delete(OTP_PREFIX + otpId);
        redisTemplate.delete(ATTEMPT_PREFIX + otpId);
        redisTemplate.delete(emailKey(data.email()));

        return data;

        }catch (OtpException ex){
            throw  new RuntimeException("Erro ao ler otp", ex);
        }
    }

    public void removerCodigo(String otpId, String email) {
        redisTemplate.delete(OTP_PREFIX + otpId);
        redisTemplate.delete(ATTEMPT_PREFIX + otpId);

        String emailKey = emailKey(email);
        String otpAtivo = redisTemplate.opsForValue().get(emailKey);
        if (otpId.equals(otpAtivo)) {
            redisTemplate.delete(emailKey);
        }
    }

    private String emailKey(String email) {
        return EMAIL_PREFIX + email.trim().toLowerCase();
    }







}
