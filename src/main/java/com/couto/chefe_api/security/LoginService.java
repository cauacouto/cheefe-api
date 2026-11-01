package com.couto.chefe_api.security;

import com.couto.chefe_api.dto.*;
import com.couto.chefe_api.exception.ContaBloqueadaException;
import com.couto.chefe_api.exception.EmailJaCadastradoException;
import com.couto.chefe_api.exception.RegistroTokenInvalidoException;
import com.couto.chefe_api.exception.RegraDeNegocioException;
import com.couto.chefe_api.service.ChefeService;
import com.couto.chefe_api.mapper.ChefeMapper;
import com.couto.chefe_api.mapper.UsuarioMapper;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.ChefeRepository;
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
    private final ModeracaoRegistry moderacaoRegistry;
    private final ContaBloqueioVerificador bloqueio;


    public LoginResult loginGoogle(GoogleUser googleUser) {
        // moderação vem da configuração, nunca do banco; é checada antes de qualquer conta
        if (moderacaoRegistry.ehModerador(googleUser.email())) {
            UsuarioAutenticado moderador = new UsuarioAutenticado(
                    moderacaoRegistry.idDe(googleUser.email()), googleUser.email(), TipoUsuario.MODERADOR);
            return new LoginResult(tokenService.gerarToken(moderador), null, TipoUsuario.MODERADOR);
        }
        return buscarConta(googleUser.email())
                .map(conta -> {
                    if (bloqueio.bloqueada(conta)) {
                        throw new ContaBloqueadaException();
                    }
                    return conta;
                })
                .map(conta -> new LoginResult(tokenService.gerarToken(conta), null, conta.tipo()))
                .orElseGet(() -> new LoginResult(null, tokenService.gerarTokenRegistro(googleUser), null));
    }

    /**
     * A conta do token ainda existe e opera? Cliente apagado some do banco; chef apagado fica com ativo=false.
     */
    public boolean contaAtiva(UsuarioAutenticado usuario) {
        return switch (usuario.tipo()) {
            case CLIENTE -> repository.existsById(usuario.id());
            case CHEFE -> chefeRepository.findById(usuario.id())
                    .map(chefe -> Boolean.TRUE.equals(chefe.getAtivo()))
                    .orElse(false);
            // removido de MODERACAO_EMAILS → sessão deixa de valer
            case MODERADOR -> moderacaoRegistry.reconhece(usuario);
        };
    }

    /** Nulo para a moderação, que não verifica telefone. */
    public Boolean telefoneVerificado(UsuarioAutenticado usuario) {
        return switch (usuario.tipo()) {
            case CLIENTE -> repository.findById(usuario.id()).map(c -> c.getTelefoneVerificadoEm() != null).orElse(false);
            case CHEFE -> chefeRepository.findById(usuario.id()).map(c -> c.getTelefoneVerificadoEm() != null).orElse(false);
            case MODERADOR -> null;
        };
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
        if (dto.getTipoUsuario() == TipoUsuario.CLIENTE && (dto.getEndereco() == null || dto.getEndereco().isEmpty())) {
            throw new RegraDeNegocioException("Informe ao menos um endereço");
        }

        return switch (dto.getTipoUsuario()) {
            case CLIENTE -> registerUsuario(dto, googleUser.email(), googleUser.foto());
            case CHEFE -> registerChefe(dto, googleUser.email(), googleUser.foto());
            case MODERADOR -> throw new RegraDeNegocioException("tipo de usuário inválido");
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
        ChefeService.exigirSemRepeticao(dto.getEspecialidades());
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
