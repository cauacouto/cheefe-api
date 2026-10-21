package com.couto.chefe_api.Security;

import com.couto.chefe_api.Dtos.ChefeResponseDto;
import com.couto.chefe_api.Dtos.LoginResult;
import com.couto.chefe_api.Dtos.RegisterRequestDto;
import com.couto.chefe_api.Dtos.RegisterResult;
import com.couto.chefe_api.Dtos.RegistroPendenteDto;
import com.couto.chefe_api.Dtos.UserResponseDto;
import com.couto.chefe_api.Excepitons.EmailJaCadastradoException;
import com.couto.chefe_api.Excepitons.RegistroTokenInvalidoException;
import com.couto.chefe_api.Mapper.ChefeMapper;
import com.couto.chefe_api.Mapper.UsuarioMapper;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.ChefeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginServiceTest {

    private static final String EMAIL = "ana@gmail.com";

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final ChefeRepository chefeRepository = mock(ChefeRepository.class);
    private final TokenService tokenService = new TokenService("teste");
    private final GoogleUser googleUser = new GoogleUser(EMAIL, "Ana", "https://foto");

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(
                usuarioRepository,
                Mappers.getMapper(UsuarioMapper.class),
                tokenService,
                Mappers.getMapper(ChefeMapper.class),
                chefeRepository
        );
        when(usuarioRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(chefeRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(usuarioRepository.save(any())).thenAnswer(inv -> {
            UserModel usuario = inv.getArgument(0);
            usuario.setId(UUID.randomUUID());
            return usuario;
        });
        when(chefeRepository.save(any())).thenAnswer(inv -> {
            ChefeModel chefe = inv.getArgument(0);
            chefe.setId(UUID.randomUUID());
            return chefe;
        });
    }

    @Test
    void clienteExistenteRecebeTokenDeAcesso() {
        UserModel usuario = new UserModel();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail(EMAIL);
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

        LoginResult result = loginService.loginGoogle(googleUser);

        UsuarioAutenticado autenticado = tokenService.validarToken(result.accessToken()).orElseThrow();
        assertEquals(TipoUsuario.CLIENTE, autenticado.tipo());
        assertEquals(usuario.getId(), autenticado.id());
        assertNull(result.registroToken());
    }

    @Test
    void chefExistenteRecebeTokenDeAcesso() {
        ChefeModel chefe = new ChefeModel();
        chefe.setId(UUID.randomUUID());
        chefe.setEmail(EMAIL);
        when(chefeRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(chefe));

        LoginResult result = loginService.loginGoogle(googleUser);

        UsuarioAutenticado autenticado = tokenService.validarToken(result.accessToken()).orElseThrow();
        assertEquals(TipoUsuario.CHEFE, autenticado.tipo());
        assertEquals(chefe.getId(), autenticado.id());
    }

    @Test
    void emailNovoRecebeTokenDeRegistro() {
        LoginResult result = loginService.loginGoogle(googleUser);

        assertNull(result.accessToken());
        assertEquals(Optional.of(googleUser), tokenService.validarTokenRegistro(result.registroToken()));
    }

    @Test
    void dadosRegistroDevolveDadosDoToken() {
        RegistroPendenteDto dados = loginService.dadosRegistro(tokenService.gerarTokenRegistro(googleUser));

        assertEquals(new RegistroPendenteDto(EMAIL, "Ana", "https://foto"), dados);
    }

    @Test
    void dadosRegistroSemTokenLanca401() {
        assertThrows(RegistroTokenInvalidoException.class, () -> loginService.dadosRegistro(null));
    }

    @Test
    void registerClienteUsaEmailDoTokenELoga() {
        RegisterResult result = loginService.register(dto(TipoUsuario.CLIENTE, List.of()), tokenRegistro());

        UserModel salvo = usuarioSalvo();
        assertEquals(EMAIL, salvo.getEmail());
        UsuarioAutenticado autenticado = tokenService.validarToken(result.accessToken()).orElseThrow();
        assertEquals(TipoUsuario.CLIENTE, autenticado.tipo());
        assertEquals(salvo.getId(), autenticado.id());
        assertInstanceOf(UserResponseDto.class, result.corpo());
    }

    @Test
    void registerChefUsaEmailDoTokenELoga() {
        RegisterResult result = loginService.register(dto(TipoUsuario.CHEFE, null), tokenRegistro());

        ArgumentCaptor<ChefeModel> captor = ArgumentCaptor.forClass(ChefeModel.class);
        verify(chefeRepository).save(captor.capture());
        assertEquals(EMAIL, captor.getValue().getEmail());
        assertEquals(TipoUsuario.CHEFE, tokenService.validarToken(result.accessToken()).orElseThrow().tipo());
        assertInstanceOf(ChefeResponseDto.class, result.corpo());
    }

    @Test
    void registerClienteUsaFotoDoGoogle() {
        loginService.register(dto(TipoUsuario.CLIENTE, List.of()), tokenRegistro());

        assertEquals("https://foto", usuarioSalvo().getImageUrl());
    }

    @Test
    void registerChefUsaFotoDoGoogle() {
        loginService.register(dto(TipoUsuario.CHEFE, null), tokenRegistro());

        ArgumentCaptor<ChefeModel> captor = ArgumentCaptor.forClass(ChefeModel.class);
        verify(chefeRepository).save(captor.capture());
        assertEquals("https://foto", captor.getValue().getImageUrl());
    }

    @Test
    void contaGoogleSemFotoGravaNulo() {
        String token = tokenService.gerarTokenRegistro(new GoogleUser(EMAIL, "Ana", null));

        loginService.register(dto(TipoUsuario.CLIENTE, List.of()), token);

        assertNull(usuarioSalvo().getImageUrl());
    }

    @Test
    void registerComEmailJaExistenteEmQualquerTabelaLanca409() {
        when(chefeRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(new ChefeModel()));

        assertThrows(EmailJaCadastradoException.class,
                () -> loginService.register(dto(TipoUsuario.CLIENTE, List.of()), tokenRegistro()));
        verify(usuarioRepository, never()).save(any());
        verify(chefeRepository, never()).save(any());
    }

    @Test
    void registerSemTokenOuComTokenDeAcessoLanca401() {
        String tokenDeAcesso = tokenService.gerarToken(new UsuarioAutenticado(UUID.randomUUID(), EMAIL, TipoUsuario.CLIENTE));

        assertThrows(RegistroTokenInvalidoException.class,
                () -> loginService.register(dto(TipoUsuario.CLIENTE, List.of()), null));
        assertThrows(RegistroTokenInvalidoException.class,
                () -> loginService.register(dto(TipoUsuario.CLIENTE, List.of()), tokenDeAcesso));
    }

    @Test
    void registerClienteSemEnderecoNaoQuebra() {
        loginService.register(dto(TipoUsuario.CLIENTE, null), tokenRegistro());

        assertTrue(usuarioSalvo().getEndereco().isEmpty());
    }

    private String tokenRegistro() {
        return tokenService.gerarTokenRegistro(googleUser);
    }

    private RegisterRequestDto dto(TipoUsuario tipo, List<com.couto.chefe_api.Dtos.EnderecoRequestDto> enderecos) {
        RegisterRequestDto dto = new RegisterRequestDto();
        dto.setTipoUsuario(tipo);
        dto.setNome("Ana");
        dto.setEndereco(enderecos);
        return dto;
    }

    private UserModel usuarioSalvo() {
        ArgumentCaptor<UserModel> captor = ArgumentCaptor.forClass(UserModel.class);
        verify(usuarioRepository).save(captor.capture());
        return captor.getValue();
    }
}
