package com.couto.chefe_api.Services;

import com.couto.chefe_api.Excepitons.ChefeNaoEncontradoException;
import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.enums.PastaImagem;
import com.couto.chefe_api.repositorys.ChefeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Troca da foto de perfil. A foto do Google é a padrão; o upload só substitui a URL depois
 * que o storage aceitou a imagem.
 */
@Service
@RequiredArgsConstructor
public class FotoPerfilService {

    private final UsuarioRepository usuarioRepository;
    private final ChefeRepository chefeRepository;
    private final ImagemStorageService imagemStorageService;

    @Transactional
    public String trocarFotoUsuario(UUID usuarioId, MultipartFile foto) {
        UserModel usuario = usuarioRepository.findById(usuarioId).orElseThrow(UsuarioException::new);
        String url = imagemStorageService.enviar(foto, PastaImagem.PERFIL);
        usuario.setImageUrl(url);
        usuarioRepository.save(usuario);
        return url;
    }

    @Transactional
    public String trocarFotoChefe(UUID chefeId, MultipartFile foto) {
        ChefeModel chefe = chefeRepository.findById(chefeId).orElseThrow(ChefeNaoEncontradoException::new);
        chefe.exigirAtivo();
        String url = imagemStorageService.enviar(foto, PastaImagem.PERFIL);
        chefe.setImageUrl(url);
        chefeRepository.save(chefe);
        return url;
    }
}
