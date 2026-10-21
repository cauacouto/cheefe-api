package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.EnderecoRequestDto;
import com.couto.chefe_api.Dtos.EnderecoResponseDto;
import com.couto.chefe_api.Excepitons.EnderecoInvalidoException;
import com.couto.chefe_api.Mapper.UsuarioMapper;
import com.couto.chefe_api.Mapper.UsuarioMapperImpl;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.repositorys.EndercoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnderecoServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EndercoRepository enderecoRepository;

    private final UsuarioMapper usuarioMapper = new UsuarioMapperImpl();
    private final UserModel usuario = new UserModel();
    private EnderecoService service;

    @BeforeEach
    void setUp() {
        service = new EnderecoService(usuarioRepository, usuarioMapper, enderecoRepository);
        usuario.setEndereco(new ArrayList<>());
        when(usuarioRepository.findByEmail("ana@x.com")).thenReturn(Optional.of(usuario));
        when(enderecoRepository.save(any(EnderecoModel.class))).thenAnswer(inv -> {
            EnderecoModel endereco = inv.getArgument(0);
            endereco.setId(42L);
            return endereco;
        });
    }

    @Test
    void salvaOEnderecoEDevolveComId() {
        EnderecoResponseDto resposta = service.adicionarEndereco(
                new EnderecoRequestDto("Rua A", "1", null, "01001000", "SP", "Centro", "SP", TipoDeResidencia.CASA),
                "ana@x.com");

        assertThat(resposta.id()).isEqualTo(42L);
        assertThat(usuario.getEndereco()).hasSize(1);
        assertThat(usuario.getEndereco().get(0).getUsuario()).isSameAs(usuario);
    }

    @Test
    void apartamentoSemComplementoNaoEhSalvo() {
        assertThatThrownBy(() -> service.adicionarEndereco(
                new EnderecoRequestDto("Rua A", "1", null, "01001000", "SP", "Centro", "SP", TipoDeResidencia.APARTAMENTO),
                "ana@x.com"))
                .isInstanceOf(EnderecoInvalidoException.class);
        verify(enderecoRepository, never()).save(any());
    }
}
