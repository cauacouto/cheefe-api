package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.EnderecoRequestDto;
import com.couto.chefe_api.Dtos.EnderecoResponseDto;
import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.Mapper.UsuarioMapper;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.EnderecoModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnderecoService {
   private final UsuarioRepository usuarioRepository;
   private final UsuarioMapper usuarioMapper;



    public EnderecoResponseDto adicionarEndereco(EnderecoRequestDto dto, String email) {
        UserModel usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(UsuarioException::new);
        EnderecoModel endereco = usuarioMapper.toModel(dto);
        endereco.getTipoDeResidencia().validar(endereco);
        endereco.setUsuario(usuario);
        usuario.getEndereco().add(endereco);
        usuarioRepository.save(usuario);
        return usuarioMapper.toDto(endereco);
    }

    public List<EnderecoResponseDto> listarEnderecos(String email) {
        UserModel usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(UsuarioException::new);
        return usuario.getEndereco().stream()
                .map(usuarioMapper::toDto)
                .toList();
    }
}
