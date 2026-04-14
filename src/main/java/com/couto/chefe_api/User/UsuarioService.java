package com.couto.chefe_api.User;

import com.couto.chefe_api.Dtos.UserRequestDto;
import com.couto.chefe_api.Dtos.UserResponseDto;
import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.Mapper.UsuarioMapper;
import com.couto.chefe_api.domin.EnderecoModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

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
                .toList();

        enderecos.forEach(e -> {
            e.getTipoDeResidencia().validar(e);
            e.setUsuario(usuario);
        });

        usuario.setEndereco(enderecos);

        var salvar = repository.save(usuario);
        return UserMapper.toDto(salvar);
    }

    public UserResponseDto atualizarUsuario(UserRequestDto dto, UUID id){
        UserModel user = repository.findById(id).orElseThrow(UsuarioException::new);
        UserMapper.toModel(dto);
        var salvar = repository.save(user);
        return UserMapper.toDto(salvar);
    }

    public Page<UserResponseDto> listarUsuarios(Pageable pageable){
        return repository.findAll(pageable)
                .map(UserMapper::toDto);


    }


}
