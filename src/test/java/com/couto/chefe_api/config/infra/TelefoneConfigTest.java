package com.couto.chefe_api.config.infra;

import com.couto.chefe_api.service.HashTelefone;
import com.couto.chefe_api.service.VerificadorTelefone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/** Qual verificador sobe em cada ambiente, e que o perfil prod não sobe sem as chaves. */
@ExtendWith(OutputCaptureExtension.class)
class TelefoneConfigTest {

    // as propriedades vazias valem mais que as variáveis de ambiente da máquina: o teste não depende delas
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(TelefoneConfig.class)
            .withPropertyValues("twilio.account-sid=", "twilio.auth-token=", "twilio.verify-service-sid=",
                    "chefhome.telefone.pepper=");

    private final ApplicationContextRunner prod = contexto
            .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"));

    @Test
    void semChavesForaDeProducaoUsaOFalso() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(VerificadorTelefone.class)).isInstanceOf(VerificadorTelefoneFalso.class);
            assertThat(ctx.getBean(HashTelefone.class).de("+5511987654321")).hasSize(64).matches("[0-9a-f]{64}");
        });
    }

    @Test
    void comAsTresChavesUsaOTwilio() {
        contexto.withPropertyValues("twilio.account-sid=AC1", "twilio.auth-token=t", "twilio.verify-service-sid=VA1")
                .run(ctx -> assertThat(ctx.getBean(VerificadorTelefone.class)).isInstanceOf(TwilioVerificadorTelefone.class));
    }

    @Test
    void chavesPelaMetadeNaoSobem() {
        contexto.withPropertyValues("twilio.account-sid=AC1").run(ctx -> assertThat(ctx).hasFailed()
                .getFailure().rootCause().hasMessageContaining("TWILIO_AUTH_TOKEN").hasMessageNotContaining("AC1"));
    }

    @Test
    void producaoSemAsChavesDoTwilioNaoSobe() {
        prod.withPropertyValues("chefhome.telefone.pepper=pepper-de-producao").run(ctx -> assertThat(ctx).hasFailed()
                .getFailure().rootCause().hasMessageContaining("TWILIO_ACCOUNT_SID")
                .hasMessageContaining("TWILIO_AUTH_TOKEN").hasMessageContaining("TWILIO_VERIFY_SERVICE_SID"));
    }

    @Test
    void producaoSemPepperNaoSobe() {
        prod.withPropertyValues("twilio.account-sid=AC1", "twilio.auth-token=t", "twilio.verify-service-sid=VA1")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("CHEFHOME_PEPPER_TELEFONE"));
    }

    @Test
    void producaoComTudoUsaOTwilio() {
        prod.withPropertyValues("twilio.account-sid=AC1", "twilio.auth-token=t", "twilio.verify-service-sid=VA1",
                        "chefhome.telefone.pepper=pepper-de-producao")
                .run(ctx -> assertThat(ctx.getBean(VerificadorTelefone.class)).isInstanceOf(TwilioVerificadorTelefone.class));
    }

    @Test
    void hashEhSha256DoPepperMaisOTelefone() throws Exception {
        HashTelefone a = new HashTelefone("pepper-a");
        HashTelefone b = new HashTelefone("pepper-b");

        assertThat(a.de("+5511987654321")).isEqualTo(sha256("pepper-a+5511987654321"))
                .isNotEqualTo(b.de("+5511987654321")).isNotEqualTo(a.de("+5511987654322"))
                .doesNotContain("987654321");
    }

    @Test
    void falsoAceitaSo000000ELogaONumeroMascarado(CapturedOutput saida) {
        VerificadorTelefoneFalso falso = new VerificadorTelefoneFalso();
        falso.enviarCodigo("+5511987654321");

        assertThat(falso.conferir("+5511987654321", "000000")).isTrue();
        assertThat(falso.conferir("+5511987654321", "123456")).isFalse();
        assertThat(saida.getAll()).contains("+55*******4321: 000000").doesNotContain("+5511987654321");
    }

    static String sha256(String texto) throws Exception {
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(hash);
    }
}
