package com.couto.chefe_api.config.infra;

import com.couto.chefe_api.exception.SmsIndisponivelException;
import com.couto.chefe_api.service.TelefoneBrasil;
import com.couto.chefe_api.service.VerificadorTelefone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Twilio Verify: o Twilio gera, envia e confere o código e limita as tentativas. Basic Auth com o SID da conta e
 * o token, que nunca vão para log; dos erros só o status HTTP é registrado (a resposta pode citar o número).
 */
@Slf4j
public class TwilioVerificadorTelefone implements VerificadorTelefone {

    static final String BASE_URL = "https://verify.twilio.com";

    private final RestClient twilio;
    private final String servicoSid;

    /** O builder chega com a fábrica de requisições (timeouts) da configuração, ou ligado a um servidor falso nos testes. */
    public TwilioVerificadorTelefone(RestClient.Builder builder, String contaSid, String token, String servicoSid) {
        this.twilio = builder
                .baseUrl(BASE_URL)
                .defaultHeaders(cabecalhos -> cabecalhos.setBasicAuth(contaSid, token))
                .build();
        this.servicoSid = servicoSid;
    }

    @Override
    public void enviarCodigo(String e164) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", e164);
        form.add("Channel", "sms");
        try {
            twilio.post()
                    .uri("/v2/Services/{servico}/Verifications", servicoSid)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            registrarFalha("enviar o SMS", e164, e);
            throw new SmsIndisponivelException(SmsIndisponivelException.AO_ENVIAR);
        }
    }

    @Override
    public boolean conferir(String e164, String codigo) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", e164);
        form.add("Code", codigo);
        try {
            Conferencia resposta = twilio.post()
                    .uri("/v2/Services/{servico}/VerificationCheck", servicoSid)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Conferencia.class);
            return resposta != null && "approved".equals(resposta.status());
        } catch (RestClientResponseException e) {
            // 404: não há verificação pendente (expirou ou já foi usada); 429: tentativas esgotadas
            int status = e.getStatusCode().value();
            if (status == 404 || status == 429) {
                return false;
            }
            registrarFalha("conferir o código", e164, e);
            throw new SmsIndisponivelException(SmsIndisponivelException.AO_CONFERIR);
        } catch (RestClientException e) {
            registrarFalha("conferir o código", e164, e);
            throw new SmsIndisponivelException(SmsIndisponivelException.AO_CONFERIR);
        }
    }

    private static void registrarFalha(String acao, String e164, RestClientException e) {
        String motivo = e instanceof RestClientResponseException resposta
                ? "HTTP " + resposta.getStatusCode().value()
                : e.getClass().getSimpleName();
        log.warn("Twilio: falha ao {} para {} ({})", acao, TelefoneBrasil.mascarar(e164), motivo);
    }

    /** Da resposta do VerificationCheck só interessa o status ("approved" = código certo). */
    record Conferencia(String status) {
    }
}
