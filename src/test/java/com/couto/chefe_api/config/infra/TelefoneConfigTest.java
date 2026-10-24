package com.couto.chefe_api.config.infra;

import com.couto.chefe_api.exception.SmsIndisponivelException;

import com.couto.chefe_api.service.HashTelefone;
import com.couto.chefe_api.service.VerificadorTelefone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Qual verificador sobe em cada ambiente: sem provedor (parado), o indisponível; o falso só pedido e fora de prod. */
@ExtendWith(OutputCaptureExtension.class)
class TelefoneConfigTest {

    // as propriedades vazias valem mais que as variáveis de ambiente da máquina: o teste não depende delas
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(TelefoneConfig.class)
            .withPropertyValues("chefhome.telefone.pepper=", "chefhome.telefone.verificador-falso=false");

    private final ApplicationContextRunner prod = contexto
            .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"));

    @Test
    void semProvedorAsRotasRespondem503() {
        // verificação parada até a escolha do canal (ex.: WhatsApp): a API sobe, e pedir/conferir dá 503
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            VerificadorTelefone verificador = ctx.getBean(VerificadorTelefone.class);
            assertThat(verificador).isInstanceOf(VerificadorTelefoneIndisponivel.class);
            assertThatThrownBy(() -> verificador.enviarCodigo("+5511987654321"))
                    .isInstanceOf(SmsIndisponivelException.class)
                    .hasMessage("Verificação de telefone ainda não disponível");
            assertThatThrownBy(() -> verificador.conferir("+5511987654321", "000000"))
                    .isInstanceOf(SmsIndisponivelException.class);
        });
    }

    @Test
    void producaoSobeSemNenhumaVariavelDeTelefone() {
        prod.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(VerificadorTelefone.class)).isInstanceOf(VerificadorTelefoneIndisponivel.class);
            // sem pepper não há hash: só seria usado com telefone verificado, que sem provedor ninguém tem
            assertThatThrownBy(() -> ctx.getBean(HashTelefone.class).de("+5511987654321"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("CHEFHOME_PEPPER_TELEFONE");
        });
    }

    @Test
    void producaoNaoAceitaOFalsoMesmoPedindo() {
        prod.withPropertyValues("chefhome.telefone.verificador-falso=true", "chefhome.telefone.pepper=pepper-de-producao-com-32-caracteres!")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("CHEFHOME_TELEFONE_FALSO"));
    }

    @Test
    void producaoComPepperCurtoNaoSobe() {
        prod.withPropertyValues("chefhome.telefone.pepper=curto")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("CHEFHOME_PEPPER_TELEFONE")
                        .hasMessageNotContaining("curto"));
    }

    @Test
    void producaoComPepperForteUsaOPepper() throws Exception {
        prod.withPropertyValues("chefhome.telefone.pepper=pepper-de-producao-com-32-caracteres!").run(ctx -> assertThat(ctx.getBean(HashTelefone.class).de("+5511987654321"))
                .isEqualTo(sha256("pepper-de-producao-com-32-caracteres!+5511987654321")));
    }

    @Test
    void comOptInForaDeProducaoUsaOFalso() {
        contexto.withPropertyValues("chefhome.telefone.verificador-falso=true").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(VerificadorTelefone.class)).isInstanceOf(VerificadorTelefoneFalso.class);
            assertThat(ctx.getBean(HashTelefone.class).de("+5511987654321")).hasSize(64).matches("[0-9a-f]{64}");
        });
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
