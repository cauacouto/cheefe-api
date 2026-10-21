package com.couto.chefe_api.Security;

import com.couto.chefe_api.Dtos.*;
import com.couto.chefe_api.Excepitons.EmailJaCadastradoException;
import com.couto.chefe_api.Excepitons.RegistroTokenInvalidoException;
import com.couto.chefe_api.Mapper.ChefeMapper;
import com.couto.chefe_api.Mapper.UsuarioMapper;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.ChefeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoginService {


    private final UsuarioRepository repository;
    private  final UsuarioMapper UserMapper;
    private final TokenService tokenService;
    private final ChefeMapper chefeMapper;
    private final ChefeRepository chefeRepository;


    public LoginResult loginGoogle(GoogleUser googleUser) {
        return buscarConta(googleUser.email())
                .map(conta -> new LoginResult(tokenService.gerarToken(conta), null))
                .orElseGet(() -> new LoginResult(null, tokenService.gerarTokenRegistro(googleUser)));
    }

    public RegistroPendenteDto dadosRegistro(String registroToken) {
        GoogleUser googleUser = validarRegistro(registroToken);
        return new RegistroPendenteDto(googleUser.email(), googleUser.nome(), googleUser.foto());
    }

    public RegisterResult register(RegisterRequestDto dto, String registroToken) {
        GoogleUser googleUser = validarRegistro(registroToken);
        if (buscarConta(googleUser.email()).isPresent()) {
            throw new EmailJaCadastradoException();
        }

        return switch (dto.getTipoUsuario()) {
            case CLIENTE -> registerUsuario(dto, googleUser.email(), googleUser.foto());
            case CHEFE -> registerChefe(dto, googleUser.email(), googleUser.foto());
        };
    }

    private RegisterResult registerUsuario(RegisterRequestDto dto, String email, String imageUrl) {
        UserModel usuario = UserMapper.toEntity(dto);
        usuario.setEmail(email);
        usuario.setImageUrl(imageUrl);

        List<EnderecoModel> enderecos = Optional.ofNullable(dto.getEndereco()).orElse(List.of()).stream()
                .map(UserMapper::toModel)
                .collect(Collectors.toCollection(ArrayList::new));
        enderecos.forEach(e -> {
            e.getTipoDeResidencia().validar(e);
            e.setUsuario(usuario);
        });
        usuario.setEndereco(enderecos);

        UserModel salvo = repository.save(usuario);
        String token = tokenService.gerarToken(new UsuarioAutenticado(salvo.getId(), salvo.getEmail(), TipoUsuario.CLIENTE));
        return new RegisterResult(token, UserMapper.toDto(salvo));
    }

    private RegisterResult registerChefe(RegisterRequestDto dto, String email, String imageUrl) {
        ChefeModel chefe = chefeMapper.toModel(dto);
        chefe.setEmail(email);
        chefe.setImageUrl(imageUrl);

        ChefeModel salvo = chefeRepository.save(chefe);
        String token = tokenService.gerarToken(new UsuarioAutenticado(salvo.getId(), salvo.getEmail(), TipoUsuario.CHEFE));
        return new RegisterResult(token, chefeMapper.toDto(salvo));
    }

    private Optional<UsuarioAutenticado> buscarConta(String email) {
        // IgnoreCase: contas da época do OTP guardam o e-mail como foi digitado
        return repository.findByEmailIgnoreCase(email)
                .map(usuario -> new UsuarioAutenticado(usuario.getId(), usuario.getEmail(), TipoUsuario.CLIENTE))
                .or(() -> chefeRepository.findByEmailIgnoreCase(email)
                        .map(chefe -> {
                            chefe.exigirAtivo();
                            return new UsuarioAutenticado(chefe.getId(), chefe.getEmail(), TipoUsuario.CHEFE);
                        }));
    }

    private GoogleUser validarRegistro(String registroToken) {
        return tokenService.validarTokenRegistro(registroToken)
                .orElseThrow(RegistroTokenInvalidoException::new);
    }
}
