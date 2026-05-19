package com.couto.chefe_api.Security;

import com.couto.chefe_api.Dtos.*;
import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.Mapper.ChefeMapper;
import com.couto.chefe_api.Mapper.UsuarioMapper;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.repositorys.ChefeRepository;
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
    private final ChefeMapper chefeMapper;
    private final ChefeRepository chefeRepository;


    public UserResponseDto RegisterUsuario(RegisterRequestDto dto){
        var senhaCriptografada = passwordEncoder.encode(dto.getPassword());
        UserModel usuario = UserMapper.toModel(dto, senhaCriptografada);
        List<EnderecoModel> enderecos = dto.getEndereco().stream()
                .map(UserMapper::toModel)
                .toList();
        enderecos.forEach(e -> {
            e.getTipoDeResidencia().validar(e);
            e.setUsuario(usuario);
        });
        usuario.setEndereco(enderecos);
        return UserMapper.toDto(repository.save(usuario));
    }



    public ChefeResponseDto registerChefe(RegisterRequestDto dto) {
        var senhaCriptografada = passwordEncoder.encode(dto.getPassword());
        ChefeModel chefe = chefeMapper.toModel(dto, senhaCriptografada);
        return chefeMapper.toDto(chefeRepository.save(chefe));
    }



    public String login(DadosLoginDto dados){
        var usuario = repository.findByEmail(dados.email())
                .orElseThrow(UsuarioException::new);


        if (!passwordEncoder.matches(dados.password(), usuario.getPassword())){
            throw new RuntimeException("senha invalida");
        }
        return tokenService.gerarToken(usuario);
    }
}
