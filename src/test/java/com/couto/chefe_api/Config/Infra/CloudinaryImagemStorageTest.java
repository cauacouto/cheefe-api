package com.couto.chefe_api.Config.Infra;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.couto.chefe_api.Excepitons.FalhaUploadImagemException;
import com.couto.chefe_api.Excepitons.ImagemInvalidaException;
import com.couto.chefe_api.enums.PastaImagem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CloudinaryImagemStorageTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P'};

    private final Cloudinary cloudinary = mock(Cloudinary.class);
    private final Uploader uploader = mock(Uploader.class);
    private CloudinaryImagemStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("secure_url", "https://cdn/prato.png"));
        storage = new CloudinaryImagemStorage(cloudinary);
    }

    @Test
    @SuppressWarnings("unchecked")
    void enviaImagemValidaParaAPastaInformada() throws IOException {
        String url = storage.enviar(arquivo("image/png", PNG), PastaImagem.PRATOS);

        assertThat(url).isEqualTo("https://cdn/prato.png");
        ArgumentCaptor<Map<String, Object>> opcoes = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(any(), opcoes.capture());
        assertThat(opcoes.getValue()).containsEntry("folder", "chefe-api/pratos");
    }

    @Test
    void aceitaJpegEWebp() {
        assertThat(storage.enviar(arquivo("image/jpeg", JPEG), PastaImagem.PRATOS)).isNotBlank();
        assertThat(storage.enviar(arquivo("image/webp", WEBP), PastaImagem.PRATOS)).isNotBlank();
    }

    @Test
    void arquivoAusenteOuVazioEhRejeitado() {
        assertThatThrownBy(() -> storage.enviar(null, PastaImagem.PRATOS))
                .isInstanceOf(ImagemInvalidaException.class);
        assertThatThrownBy(() -> storage.enviar(arquivo("image/png", new byte[0]), PastaImagem.PRATOS))
                .isInstanceOf(ImagemInvalidaException.class);
        verifyNoInteractions(uploader);
    }

    @Test
    void tipoNaoPermitidoEhRejeitadoAntesDoUpload() {
        assertThatThrownBy(() -> storage.enviar(arquivo("image/gif", "GIF89a".getBytes()), PastaImagem.PRATOS))
                .isInstanceOf(ImagemInvalidaException.class);
        assertThatThrownBy(() -> storage.enviar(arquivo(null, PNG), PastaImagem.PRATOS))
                .isInstanceOf(ImagemInvalidaException.class);
        verifyNoInteractions(uploader);
    }

    @Test
    void conteudoQueNaoEhImagemEhRejeitadoMesmoComContentTypeDeImagem() {
        assertThatThrownBy(() -> storage.enviar(arquivo("image/png", "<script>alert(1)</script>".getBytes()), PastaImagem.PRATOS))
                .isInstanceOf(ImagemInvalidaException.class);
        assertThatThrownBy(() -> storage.enviar(arquivo("image/jpeg", PNG), PastaImagem.PRATOS))
                .isInstanceOf(ImagemInvalidaException.class);
        verifyNoInteractions(uploader);
    }

    @Test
    void imagemAcimaDe5MbEhRejeitadaAntesDoUpload() {
        byte[] grande = Arrays.copyOf(PNG, 5 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> storage.enviar(arquivo("image/png", grande), PastaImagem.PRATOS))
                .isInstanceOf(ImagemInvalidaException.class);
        verifyNoInteractions(uploader);
    }

    @Test
    void falhaDoCloudinaryViraFalhaUpload() throws IOException {
        when(uploader.upload(any(), anyMap())).thenThrow(new IOException("timeout"));

        assertThatThrownBy(() -> storage.enviar(arquivo("image/png", PNG), PastaImagem.PRATOS))
                .isInstanceOf(FalhaUploadImagemException.class);
    }

    private static MockMultipartFile arquivo(String contentType, byte[] conteudo) {
        return new MockMultipartFile("foto", "foto", contentType, conteudo);
    }
}
