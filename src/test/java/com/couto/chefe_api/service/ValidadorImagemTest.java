package com.couto.chefe_api.service;

import com.couto.chefe_api.exception.ImagemInvalidaException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorImagemTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P'};

    @Test
    void imagemValidaDevolveConteudoTipoEExtensao() {
        ImagemValidada png = ValidadorImagem.validar(arquivo("image/png", PNG));

        assertThat(png.conteudo()).isEqualTo(PNG);
        assertThat(png.contentType()).isEqualTo("image/png");
        assertThat(png.extensao()).isEqualTo("png");
        assertThat(ValidadorImagem.validar(arquivo("image/jpeg", JPEG)).extensao()).isEqualTo("jpg");
        assertThat(ValidadorImagem.validar(arquivo("image/webp", WEBP)).extensao()).isEqualTo("webp");
    }

    @Test
    void arquivoAusenteOuVazioEhRejeitado() {
        assertThatThrownBy(() -> ValidadorImagem.validar(null))
                .isInstanceOf(ImagemInvalidaException.class);
        assertThatThrownBy(() -> ValidadorImagem.validar(arquivo("image/png", new byte[0])))
                .isInstanceOf(ImagemInvalidaException.class);
    }

    @Test
    void tipoNaoPermitidoEhRejeitado() {
        assertThatThrownBy(() -> ValidadorImagem.validar(arquivo("image/gif", "GIF89a".getBytes())))
                .isInstanceOf(ImagemInvalidaException.class);
        assertThatThrownBy(() -> ValidadorImagem.validar(arquivo(null, PNG)))
                .isInstanceOf(ImagemInvalidaException.class);
    }

    @Test
    void conteudoQueNaoEhImagemEhRejeitadoMesmoComContentTypeDeImagem() {
        assertThatThrownBy(() -> ValidadorImagem.validar(arquivo("image/png", "<script>alert(1)</script>".getBytes())))
                .isInstanceOf(ImagemInvalidaException.class);
        assertThatThrownBy(() -> ValidadorImagem.validar(arquivo("image/jpeg", PNG)))
                .isInstanceOf(ImagemInvalidaException.class);
    }

    @Test
    void imagemAcimaDe5MbEhRejeitada() {
        byte[] grande = Arrays.copyOf(PNG, 5 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> ValidadorImagem.validar(arquivo("image/png", grande)))
                .isInstanceOf(ImagemInvalidaException.class);
    }

    static MockMultipartFile arquivo(String contentType, byte[] conteudo) {
        return new MockMultipartFile("foto", "foto", contentType, conteudo);
    }
}
