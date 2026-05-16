package com.couto.chefe_api.Security;

import com.couto.chefe_api.Dtos.DadosLoginDto;
import com.couto.chefe_api.Dtos.UserRequestDto;
import com.couto.chefe_api.Dtos.UserResponseDto;
import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.Mapper.UsuarioMapper;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.EnderecoModel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginService {


    private final UsuarioRepository repository;
    private  final UsuarioMapper UserMapper;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;


    public UserResponseDto Register(UserRequestDto dto){

        var senhaCriptografada = passwordEncoder.encode(dto.password());

        UserModel usuario = UserMapper.toModel(dto,senhaCriptografada);

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


    public String login(DadosLoginDto dados){
        var usuario = repository.findByEmail(dados.email())
                .orElseThrow(UsuarioException::new);


        if (!passwordEncoder.matches(dados.passaword(), usuario.getPassword())){
            throw new RuntimeException("senha invalida");
        }
        return tokenService.gerarToken(usuario);
    }
}
