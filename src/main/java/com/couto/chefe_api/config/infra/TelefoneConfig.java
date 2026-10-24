package com.couto.chefe_api.config.infra;

import com.couto.chefe_api.service.HashTelefone;
import com.couto.chefe_api.service.VerificadorTelefone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

/**
 * Verificação de telefone parada até a escolha do canal (ex.: WhatsApp): sem provedor, pedir e conferir código
 * respondem 503, e nada no app exige telefone. O verificador falso (código 000000) só quando pedido
 * explicitamente (CHEFHOME_TELEFONE_FALSO=true, desenvolvimento e testes) e nunca em prod. As mensagens citam
 * só o nome das variáveis, nunca valores.
 */
@Slf4j
@Configuration
public class TelefoneConfig {

    /** Fora de prod: valor fixo de desenvolvimento (os hashes de teste não valem em lugar nenhum). */
    static final String PEPPER_DESENVOLVIMENTO = "chefhome-pepper-de-desenvolvimento";

    /** O pepper protege os hashes de telefone banido (o espaço de celulares é pequeno): curto demais é adivinhável. */
    static final int PEPPER_MINIMO = 32;

    private static final Profiles PROD = Profiles.of("prod");

    @Bean
    public VerificadorTelefone verificadorTelefone(
            Environment ambiente,
            @Value("${chefhome.telefone.verificador-falso:${CHEFHOME_TELEFONE_FALSO:false}}") boolean falsoPedido) {
        if (!falsoPedido) {
            return new VerificadorTelefoneIndisponivel();
        }
        if (ambiente.acceptsProfiles(PROD)) {
            throw new IllegalStateException("CHEFHOME_TELEFONE_FALSO não vale em produção: remova a variável");
        }
        log.warn("Verificação de telefone FALSA (código 000000). Nunca use assim em produção.");
        return new VerificadorTelefoneFalso();
    }

    /**
     * Em prod, pepper informado precisa ser forte; sem pepper, o hash recusa ser usado (só seria com telefone
     * verificado, que sem provedor ninguém tem). Ligar um provedor exige definir CHEFHOME_PEPPER_TELEFONE.
     */
    @Bean
    public HashTelefone hashTelefone(Environment ambiente,
                                     @Value("${chefhome.telefone.pepper:${CHEFHOME_PEPPER_TELEFONE:}}") String pepper) {
        if (ambiente.acceptsProfiles(PROD)) {
            if (!StringUtils.hasText(pepper)) {
                return new HashTelefone(null);
            }
            if (pepper.length() < PEPPER_MINIMO) {
                throw new IllegalStateException("Hash de telefone banido mal configurado: CHEFHOME_PEPPER_TELEFONE "
                        + "precisa de pelo menos " + PEPPER_MINIMO + " caracteres (openssl rand -base64 32)");
            }
            return new HashTelefone(pepper);
        }
        return new HashTelefone(StringUtils.hasText(pepper) ? pepper : PEPPER_DESENVOLVIMENTO);
    }
}
