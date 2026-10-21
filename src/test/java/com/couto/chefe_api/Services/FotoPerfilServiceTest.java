package com.couto.chefe_api.Services;

import com.couto.chefe_api.Excepitons.ContaInativaException;
import com.couto.chefe_api.Excepitons.ImagemInvalidaException;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.enums.PastaImagem;
import com.couto.chefe_api.repositorys.ChefeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FotoPerfilServiceTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final ChefeRepository chefeRepository = mock(ChefeRepository.class);
    private final ImagemStorageService storage = mock(ImagemStorageService.class);
    private final FotoPerfilService service = new FotoPerfilService(usuarioRepository, chefeRepository, storage);
    private final MockMultipartFile foto = new MockMultipartFile("foto", "f.png", "image/png", new byte[]{1});

    @Test
    void trocaFotoDoUsuarioGravaUrl() {
        UserModel usuario = new UserModel();
        usuario.setId(UUID.randomUUID());
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(storage.enviar(foto, PastaImagem.PERFIL)).thenReturn("https://cdn/perfil/x.png");

        String url = service.trocarFotoUsuario(usuario.getId(), foto);

        assertThat(url).isEqualTo("https://cdn/perfil/x.png");
        assertThat(usuario.getImageUrl()).isEqualTo("https://cdn/perfil/x.png");
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void fotoInvalidaMantemAnterior() {
        UserModel usuario = new UserModel();
        usuario.setId(UUID.randomUUID());
        usuario.setImageUrl("https://antiga");
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(storage.enviar(any(), any())).thenThrow(new ImagemInvalidaException("Envie uma imagem"));

        assertThatThrownBy(() -> service.trocarFotoUsuario(usuario.getId(), foto))
                .isInstanceOf(ImagemInvalidaException.class);
        assertThat(usuario.getImageUrl()).isEqualTo("https://antiga");
    }

    @Test
    void chefApagadoNaoTrocaFoto() {
        ChefeModel chefe = new ChefeModel();
        chefe.setId(UUID.randomUUID());
        chefe.setAtivo(false);
        when(chefeRepository.findById(chefe.getId())).thenReturn(Optional.of(chefe));

        assertThatThrownBy(() -> service.trocarFotoChefe(chefe.getId(), foto))
                .isInstanceOf(ContaInativaException.class);
        verifyNoInteractions(storage);
    }
}
