package com.couto.chefe_api.service;

import com.couto.chefe_api.exception.ImagemInvalidaException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Regras de imagem independentes do fornecedor de armazenamento: presença, tamanho (5MB),
 * tipo permitido e assinatura do conteúdo batendo com o Content-Type informado pelo cliente.
 */
public final class ValidadorImagem {

    static final long TAMANHO_MAXIMO = 5 * 1024 * 1024; // 5MB

    private static final byte[] ASSINATURA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] ASSINATURA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private ValidadorImagem() {
    }

    public static ImagemValidada validar(MultipartFile arquivo) {
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

        String contentType = String.valueOf(arquivo.getContentType());
        // O Content-Type vem do cliente; o conteúdo precisa bater com ele.
        String extensao = switch (contentType) {
            case "image/jpeg" -> exigir(comecaCom(conteudo, ASSINATURA_JPEG, 0), "jpg");
            case "image/png" -> exigir(comecaCom(conteudo, ASSINATURA_PNG, 0), "png");
            case "image/webp" -> exigir(comecaCom(conteudo, RIFF, 0) && comecaCom(conteudo, WEBP, 8), "webp");
            default -> throw new ImagemInvalidaException("Apenas imagens JPEG, PNG ou WEBP são permitidas");
        };
        return new ImagemValidada(conteudo, contentType, extensao);
    }

    private static String exigir(boolean valido, String extensao) {
        if (!valido) {
            throw new ImagemInvalidaException("O conteúdo do arquivo não corresponde ao tipo de imagem informado");
        }
        return extensao;
    }

    private static boolean comecaCom(byte[] conteudo, byte[] assinatura, int deslocamento) {
        return conteudo.length >= deslocamento + assinatura.length
                && Arrays.equals(conteudo, deslocamento, deslocamento + assinatura.length, assinatura, 0, assinatura.length);
    }
}
