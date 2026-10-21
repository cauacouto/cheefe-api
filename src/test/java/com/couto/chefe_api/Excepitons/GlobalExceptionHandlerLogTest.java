package com.couto.chefe_api.Excepitons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerLogTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void falhaDeServicoExternoRegistraACausaNoLog(CapturedOutput output) {
        handler.handleNegocio(new FalhaUploadImagemException(new IllegalStateException("SignatureDoesNotMatch vindo do R2")));

        assertThat(output).contains("SignatureDoesNotMatch vindo do R2");
    }

    @Test
    void erroDoClienteNaoPoluiOLog(CapturedOutput output) {
        handler.handleNegocio(new ImagemInvalidaException("Envie uma imagem"));

        assertThat(output).doesNotContain("Envie uma imagem");
    }
}
