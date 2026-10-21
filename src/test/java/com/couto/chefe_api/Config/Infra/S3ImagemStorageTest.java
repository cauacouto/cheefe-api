package com.couto.chefe_api.Config.Infra;

import com.couto.chefe_api.Excepitons.FalhaUploadImagemException;
import com.couto.chefe_api.Excepitons.ImagemInvalidaException;
import com.couto.chefe_api.enums.PastaImagem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class S3ImagemStorageTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};

    private final S3Client s3 = mock(S3Client.class);
    private final S3ImagemStorage storage = new S3ImagemStorage(s3, "imagens", "https://pub-abc.r2.dev");

    @Test
    void enviaParaOBucketNaPastaComTipoEDevolveUrlPublica() {
        String url = storage.enviar(arquivo("image/png", PNG), PastaImagem.PRATOS);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("imagens");
        assertThat(request.getValue().key()).matches("chefe-api/pratos/[0-9a-f-]{36}\\.png");
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(body.getValue().optionalContentLength()).hasValue((long) PNG.length);
        assertThat(url).isEqualTo("https://pub-abc.r2.dev/" + request.getValue().key());
    }

    @Test
    void cadaEnvioGeraUmaChaveDiferente() {
        String primeira = storage.enviar(arquivo("image/png", PNG), PastaImagem.PRATOS);
        String segunda = storage.enviar(arquivo("image/png", PNG), PastaImagem.PRATOS);

        assertThat(primeira).isNotEqualTo(segunda);
    }

    @Test
    void publicUrlComBarraFinalNaoDuplicaBarra() {
        S3ImagemStorage comBarra = new S3ImagemStorage(s3, "imagens", "https://pub-abc.r2.dev/");

        assertThat(comBarra.enviar(arquivo("image/png", PNG), PastaImagem.PRATOS))
                .startsWith("https://pub-abc.r2.dev/chefe-api/pratos/");
    }

    @Test
    void arquivoInvalidoNuncaChegaAoStorage() {
        assertThatThrownBy(() -> storage.enviar(arquivo("image/png", "nao-e-png".getBytes()), PastaImagem.PRATOS))
                .isInstanceOf(ImagemInvalidaException.class);
        verifyNoInteractions(s3);
    }

    @Test
    void falhaDoStorageViraFalhaUpload() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("timeout"));

        assertThatThrownBy(() -> storage.enviar(arquivo("image/png", PNG), PastaImagem.PRATOS))
                .isInstanceOf(FalhaUploadImagemException.class);
    }

    private static MockMultipartFile arquivo(String contentType, byte[] conteudo) {
        return new MockMultipartFile("foto", "foto", contentType, conteudo);
    }
}
