package com.couto.chefe_api.User;

import com.couto.chefe_api.Dtos.UserRequestDto;
import com.couto.chefe_api.Dtos.UserResponseDto;
import com.couto.chefe_api.Mapper.UsuarioMapper;
import com.couto.chefe_api.domin.EnderecoModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioService {

private final UsuarioRepository repository;
private  final UsuarioMapper UserMapper;


    public UsuarioService(UsuarioRepository repository, UsuarioMapper userMapper) {
        this.repository = repository;
        UserMapper = userMapper;

    }

    public UserResponseDto salvarUsuario(UserRequestDto dto){
        UserModel usuario = UserMapper.toModel(dto);

        List<EnderecoModel> enderecos = dto.endereco().stream()
                .map(UserMapper::toModel)
                .peek(e -> e.getTipoDeResidencia().validar(e))
                .toList();

        usuario.setEndereco(enderecos);

        var salvar = repository.save(usuario);
        return UserMapper.toDto(salvar);
    }
}
