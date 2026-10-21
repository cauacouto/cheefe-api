package com.couto.chefe_api.Config.Infra;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.couto.chefe_api.Excepitons.FalhaUploadImagemException;
import com.couto.chefe_api.Services.ImagemStorageService;
import com.couto.chefe_api.Services.ValidadorImagem;
import com.couto.chefe_api.enums.PastaImagem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CloudinaryImagemStorage implements ImagemStorageService {

    private final Cloudinary cloudinary;

    @Override
    public String enviar(MultipartFile arquivo, PastaImagem pasta) {
        byte[] conteudo = ValidadorImagem.validar(arquivo).conteudo();
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

}
