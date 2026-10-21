package com.couto.chefe_api.Config.Infra;

import com.couto.chefe_api.Excepitons.FalhaUploadImagemException;
import com.couto.chefe_api.Services.ImagemStorageService;
import com.couto.chefe_api.Services.ImagemValidada;
import com.couto.chefe_api.Services.ValidadorImagem;
import com.couto.chefe_api.enums.PastaImagem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

/**
 * Armazena imagens em um bucket compatível com S3 (Cloudflare R2, AWS S3, MinIO...).
 * O bucket precisa ter leitura pública em {@code storage.public-url}.
 */
@Component
public class S3ImagemStorage implements ImagemStorageService {

    private final S3Client s3;
    private final String bucket;
    private final String publicUrl;

    public S3ImagemStorage(S3Client s3,
                           @Value("${storage.bucket}") String bucket,
                           @Value("${storage.public-url}") String publicUrl) {
        this.s3 = s3;
        this.bucket = bucket;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    @Override
    public String enviar(MultipartFile arquivo, PastaImagem pasta) {
        ImagemValidada imagem = ValidadorImagem.validar(arquivo);
        String chave = pasta.getCaminho() + "/" + UUID.randomUUID() + "." + imagem.extensao();

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(chave)
                .contentType(imagem.contentType())
                .build();
        try {
            s3.putObject(request, RequestBody.fromBytes(imagem.conteudo()));
        } catch (SdkException e) {
            throw new FalhaUploadImagemException(e);
        }
        return publicUrl + "/" + chave;
    }
}
