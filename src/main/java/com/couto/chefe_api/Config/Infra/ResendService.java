package com.couto.chefe_api.Config.Infra;

import com.couto.chefe_api.Dtos.EmailRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResendService {


    private final WebclientResendConfig resendWebClient;

    @Value("${resend.secret-key}")
    private String apiKey;


    public String enviarOtp(String email, String codigo) {

       EmailRequest request = new EmailRequest(
               "ChefHome <onboarding@resend.dev>",
                new String[]{email},
                "Código OTP",
                "<h1>" + codigo + "</h1>"
        );

         return resendWebClient.resendWebClient().post()
                .uri("/emails")
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + apiKey
                )
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                 .block();

    }


}
