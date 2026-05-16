package com.couto.chefe_api.User;

import com.couto.chefe_api.Dtos.UserRequestDto;
import com.couto.chefe_api.Dtos.UserResponseDto;
import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.Mapper.UsuarioMapper;
import com.couto.chefe_api.domin.EnderecoModel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository repository;
    private  final UsuarioMapper UserMapper;
    private final PasswordEncoder passwordEncoder;



    public UserResponseDto atualizarUsuario(UserRequestDto dto, UUID id){
        UserModel user = repository.findById(id).orElseThrow(UsuarioException::new);

        var senhaCriptografada = passwordEncoder.encode(dto.password());
        user.setPassword(senhaCriptografada);
        user.setNome(dto.nome());
        user.setEmail(dto.email());
        user.setTelefone(dto.telefone());

         List<EnderecoModel> endecos = dto.endereco().stream()
                 .map(UserMapper::toModel)
                 .toList();

         endecos.forEach(e ->{
             e.getTipoDeResidencia().validar(e);
             e.setUsuario(user);
         });
         user.setEndereco(endecos);

         var salvar = repository.save(user);
        return UserMapper.toDto(salvar);
    }

    public Page<UserResponseDto> listarUsuarios(Pageable pageable){
        return repository.findAll(pageable)
                .map(UserMapper::toDto);


    }

    public UserResponseDto buscarPorId(UUID id){
        UserModel user = repository.findById(id)
                .orElseThrow(UsuarioException::new);
        return UserMapper.toDto(user);
    }

    public void deletar(UUID id){
        repository.deleteById(id);
    }


}
