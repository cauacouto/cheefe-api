package com.couto.chefe_api.service;

import com.couto.chefe_api.dto.EnderecoRequestDto;
import com.couto.chefe_api.dto.EnderecoResponseDto;
import com.couto.chefe_api.exception.UsuarioException;
import com.couto.chefe_api.mapper.UsuarioMapper;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.repository.EnderecoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnderecoService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final EnderecoRepository enderecoRepository;

    @Transactional
    public EnderecoResponseDto adicionarEndereco(EnderecoRequestDto dto, UUID usuarioId) {
        UserModel usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(UsuarioException::new);
        EnderecoModel endereco = usuarioMapper.toModel(dto);
        endereco.getTipoDeResidencia().validar(endereco);
        endereco.setUsuario(usuario);

        EnderecoModel salvo = enderecoRepository.save(endereco);
        usuario.getEndereco().add(salvo);
        return usuarioMapper.toDto(salvo);
    }

    @Transactional(readOnly = true)
    public List<EnderecoResponseDto> listarEnderecos(UUID usuarioId) {
        UserModel usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(UsuarioException::new);
        return usuario.getEndereco().stream()
                .map(usuarioMapper::toDto)
                .toList();
    }
}
