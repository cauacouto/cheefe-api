package com.couto.chefe_api.config.infra;

import com.couto.chefe_api.exception.SmsIndisponivelException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Contra um servidor HTTP falso (MockRestServiceServer): nenhuma chamada sai para a rede. */
@ExtendWith(OutputCaptureExtension.class)
class TwilioVerificadorTelefoneTest {

    static final String CONTA = "ACteste0000000000000000000000000";
    static final String TOKEN = "token-secreto-de-teste";
    static final String SERVICO = "VAteste0000000000000000000000000";
    static final String TELEFONE = "+5511987654321";
    static final String BASE = "https://verify.twilio.com/v2/Services/" + SERVICO;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer twilio = MockRestServiceServer.bindTo(builder).build();
    private final TwilioVerificadorTelefone verificador = new TwilioVerificadorTelefone(builder, CONTA, TOKEN, SERVICO);

    static String basic() {
        return "Basic " + Base64.getEncoder().encodeToString((CONTA + ":" + TOKEN).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void enviarCodigoPedeSmsComBasicAuth() {
        twilio.expect(requestTo(BASE + "/Verifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basic()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(java.util.Map.of("To", TELEFONE, "Channel", "sms")))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"pending\"}"));

        verificador.enviarCodigo(TELEFONE);

        twilio.verify();
    }

    @Test
    void conferirAprovadoSoComStatusApproved() {
        twilio.expect(requestTo(BASE + "/VerificationCheck"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basic()))
                .andExpect(content().formDataContains(java.util.Map.of("To", TELEFONE, "Code", "123456")))
                .andRespond(withSuccess("{\"status\":\"approved\",\"valid\":true,\"sid\":\"VE1\"}", MediaType.APPLICATION_JSON));
        twilio.expect(requestTo(BASE + "/VerificationCheck"))
                .andRespond(withSuccess("{\"status\":\"pending\",\"valid\":false}", MediaType.APPLICATION_JSON));

        assertThat(verificador.conferir(TELEFONE, "123456")).isTrue();
        assertThat(verificador.conferir(TELEFONE, "654321")).isFalse();
        twilio.verify();
    }

    @Test
    void verificacaoExpiradaOuTentativasEsgotadasNaoAprova() {
        // o Twilio responde 404 sem verificação pendente (expirou ou já foi usada) e 429 com tentativas esgotadas
        twilio.expect(requestTo(BASE + "/VerificationCheck")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":20404}"));
        twilio.expect(requestTo(BASE + "/VerificationCheck")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":60202}"));

        assertThat(verificador.conferir(TELEFONE, "123456")).isFalse();
        assertThat(verificador.conferir(TELEFONE, "123456")).isFalse();
    }

    @Test
    void twilioForaDa503SemVazarAResposta(CapturedOutput saida) {
        String respostaDoTwilio = "{\"message\":\"Invalid parameter To: " + TELEFONE + "\",\"code\":60200}";
        twilio.expect(requestTo(BASE + "/Verifications")).andRespond(withServerError().body(respostaDoTwilio));
        twilio.expect(requestTo(BASE + "/Verifications")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body(respostaDoTwilio));
        twilio.expect(requestTo(BASE + "/VerificationCheck")).andRespond(withServerError().body(respostaDoTwilio));

        assertThatThrownBy(() -> verificador.enviarCodigo(TELEFONE))
                .isInstanceOfSatisfying(SmsIndisponivelException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).isEqualTo("Não foi possível enviar o SMS agora");
                    assertThat(e.getCause()).isNull();
                });
        assertThatThrownBy(() -> verificador.enviarCodigo(TELEFONE)).isInstanceOf(SmsIndisponivelException.class);
        assertThatThrownBy(() -> verificador.conferir(TELEFONE, "123456"))
                .isInstanceOfSatisfying(SmsIndisponivelException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        // o log diz o que houve, mas sem token, sem cabeçalho de autenticação e sem o número inteiro
        assertThat(saida.getAll()).contains("+55*******4321").doesNotContain(TOKEN).doesNotContain(basic())
                .doesNotContain(TELEFONE).doesNotContain("Invalid parameter");
    }
}
