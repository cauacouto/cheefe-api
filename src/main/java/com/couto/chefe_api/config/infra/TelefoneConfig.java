package com.couto.chefe_api.config.infra;

import com.couto.chefe_api.service.HashTelefone;
import com.couto.chefe_api.service.VerificadorTelefone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verificação de telefone: Twilio com as três chaves; o verificador falso (código 000000) só quando pedido
 * explicitamente (CHEFHOME_TELEFONE_FALSO=true, desenvolvimento e testes) e nunca em prod. Em prod faltar
 * qualquer chave ou um pepper forte derruba a inicialização: SMS de mentira ou hash previsível em produção seria
 * pior que não subir. As mensagens citam só o nome das variáveis, nunca valores.
 */
@Slf4j
@Configuration
public class TelefoneConfig {

    /** Fora de prod: valor fixo de desenvolvimento (os hashes de teste não valem em lugar nenhum). */
    static final String PEPPER_DESENVOLVIMENTO = "chefhome-pepper-de-desenvolvimento";

    private static final Profiles PROD = Profiles.of("prod");

    /** O pepper protege os hashes de telefone banido (o espaço de celulares é pequeno): curto demais é adivinhável. */
    static final int PEPPER_MINIMO = 32;

    @Bean
    public VerificadorTelefone verificadorTelefone(
            Environment ambiente,
            @Value("${twilio.account-sid:${TWILIO_ACCOUNT_SID:}}") String contaSid,
            @Value("${twilio.auth-token:${TWILIO_AUTH_TOKEN:}}") String token,
            @Value("${twilio.verify-service-sid:${TWILIO_VERIFY_SERVICE_SID:}}") String servicoSid,
            @Value("${chefhome.telefone.verificador-falso:${CHEFHOME_TELEFONE_FALSO:false}}") boolean falsoPedido) {
        boolean prod = ambiente.acceptsProfiles(PROD);
        if (prod && falsoPedido) {
            throw new IllegalStateException("CHEFHOME_TELEFONE_FALSO não vale em produção: remova a variável");
        }
        Map<String, String> chaves = new LinkedHashMap<>();
        chaves.put("TWILIO_ACCOUNT_SID", contaSid);
        chaves.put("TWILIO_AUTH_TOKEN", token);
        chaves.put("TWILIO_VERIFY_SERVICE_SID", servicoSid);
        List<String> faltando = new ArrayList<>();
        chaves.forEach((nome, valor) -> {
            if (!StringUtils.hasText(valor)) {
                faltando.add(nome);
            }
        });

        if (faltando.isEmpty()) {
            return new TwilioVerificadorTelefone(clienteHttp(), contaSid, token, servicoSid);
        }
        if (prod || faltando.size() < chaves.size()) {
            throw new IllegalStateException("Verificação de telefone sem configuração: defina " + String.join(", ", faltando)
                    + (faltando.size() < chaves.size() ? " (o Twilio precisa das três variáveis)" : ""));
        }
        if (!falsoPedido) {
            throw new IllegalStateException("Verificação de telefone sem configuração: defina as variáveis TWILIO_* "
                    + "ou, só em desenvolvimento, CHEFHOME_TELEFONE_FALSO=true");
        }
        log.warn("Twilio não configurado: verificação de telefone FALSA (código 000000). Nunca use assim em produção.");
        return new VerificadorTelefoneFalso();
    }

    @Bean
    public HashTelefone hashTelefone(Environment ambiente,
                                     @Value("${chefhome.telefone.pepper:${CHEFHOME_PEPPER_TELEFONE:}}") String pepper) {
        if (ambiente.acceptsProfiles(PROD)) {
            if (!StringUtils.hasText(pepper) || pepper.length() < PEPPER_MINIMO) {
                throw new IllegalStateException("Hash de telefone banido sem configuração: defina CHEFHOME_PEPPER_TELEFONE "
                        + "com pelo menos " + PEPPER_MINIMO + " caracteres (openssl rand -base64 32)");
            }
            return new HashTelefone(pepper);
        }
        if (StringUtils.hasText(pepper)) {
            return new HashTelefone(pepper);
        }
        return new HashTelefone(PEPPER_DESENVOLVIMENTO);
    }

    /** Timeouts curtos, como no ViaCep: pedir o SMS não pode prender a requisição. */
    private static RestClient.Builder clienteHttp() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(fabrica);
    }
}
