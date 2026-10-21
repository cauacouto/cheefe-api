package com.couto.chefe_api.Config.Infra;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.couto.chefe_api.Excepitons.FalhaUploadImagemException;
import com.couto.chefe_api.Excepitons.ImagemInvalidaException;
import com.couto.chefe_api.Services.ImagemStorageService;
import com.couto.chefe_api.enums.PastaImagem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CloudinaryImagemStorage implements ImagemStorageService {

    static final long TAMANHO_MAXIMO = 5 * 1024 * 1024; // 5MB

    private static final byte[] ASSINATURA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] ASSINATURA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private final Cloudinary cloudinary;

    @Override
    public String enviar(MultipartFile arquivo, PastaImagem pasta) {
        byte[] conteudo = validar(arquivo);
        try {
            Map<?, ?> resultado = cloudinary.uploader().upload(
                    conteudo,
                    ObjectUtils.asMap("folder", pasta.getCaminho(), "resource_type", "image"));
            Object url = resultado == null ? null : resultado.get("secure_url");
            if (url == null) {
                throw new IllegalStateException("Cloudinary não devolveu secure_url");
            }
            return url.toString();
        } catch (IOException | RuntimeException e) {
            throw new FalhaUploadImagemException(e);
        }
    }

    private static byte[] validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ImagemInvalidaException("Envie uma imagem");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new ImagemInvalidaException("Imagem muito grande (máximo 5MB)");
        }

        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException e) {
            throw new ImagemInvalidaException("Não foi possível ler a imagem");
        }

        // O Content-Type vem do cliente; o conteúdo precisa bater com ele.
        boolean valido = switch (String.valueOf(arquivo.getContentType())) {
            case "image/jpeg" -> comecaCom(conteudo, ASSINATURA_JPEG, 0);
            case "image/png" -> comecaCom(conteudo, ASSINATURA_PNG, 0);
            case "image/webp" -> comecaCom(conteudo, RIFF, 0) && comecaCom(conteudo, WEBP, 8);
            default -> throw new ImagemInvalidaException("Apenas imagens JPEG, PNG ou WEBP são permitidas");
        };
        if (!valido) {
            throw new ImagemInvalidaException("O conteúdo do arquivo não corresponde ao tipo de imagem informado");
        }
        return conteudo;
    }

    private static boolean comecaCom(byte[] conteudo, byte[] assinatura, int deslocamento) {
        return conteudo.length >= deslocamento + assinatura.length
                && Arrays.equals(conteudo, deslocamento, deslocamento + assinatura.length, assinatura, 0, assinatura.length);
    }
}
