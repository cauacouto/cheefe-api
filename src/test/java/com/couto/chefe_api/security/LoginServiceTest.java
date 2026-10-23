package com.couto.chefe_api.security;

import com.couto.chefe_api.dto.ChefeResponseDto;
import com.couto.chefe_api.dto.LoginResult;
import com.couto.chefe_api.dto.RegisterRequestDto;
import com.couto.chefe_api.dto.RegisterResult;
import com.couto.chefe_api.dto.RegistroPendenteDto;
import com.couto.chefe_api.dto.UserResponseDto;
import com.couto.chefe_api.exception.ContaBloqueadaException;
import com.couto.chefe_api.exception.ContaInativaException;
import com.couto.chefe_api.exception.EmailJaCadastradoException;
import com.couto.chefe_api.exception.RegistroTokenInvalidoException;
import com.couto.chefe_api.exception.RegraDeNegocioException;
import com.couto.chefe_api.mapper.ChefeMapper;
import com.couto.chefe_api.mapper.UsuarioMapper;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.ChefeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                chefeRepository,
                new ModeracaoRegistry("moderacao@chefhome.test"),
                new ContaBloqueioVerificador(usuarioRepository, chefeRepository)
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
    void contaBloqueadaNaoLoga() {
        UserModel usuario = new UserModel();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail(EMAIL);
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsByIdAndBloqueadoEmIsNotNull(usuario.getId())).thenReturn(true);

        assertThrows(ContaBloqueadaException.class, () -> loginService.loginGoogle(googleUser));
    }

    @Test
    void chefBloqueadoNaoLoga() {
        ChefeModel chefe = new ChefeModel();
        chefe.setId(UUID.randomUUID());
        chefe.setEmail(EMAIL);
        chefe.setAtivo(true);
        when(chefeRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(chefe));
        when(chefeRepository.existsByIdAndBloqueadoEmIsNotNull(chefe.getId())).thenReturn(true);

        assertThrows(ContaBloqueadaException.class, () -> loginService.loginGoogle(googleUser));
    }

    @Test
    void chefApagadoNaoRecebeToken() {
        ChefeModel chefe = new ChefeModel();
        chefe.setId(UUID.randomUUID());
        chefe.setEmail(EMAIL);
        chefe.setAtivo(false);
        when(chefeRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(chefe));

        assertThrows(ContaInativaException.class, () -> loginService.loginGoogle(googleUser));
    }

    @Test
    void emailDeModeradorRecebeTokenModerador() {
        UserModel conta = new UserModel();
        conta.setId(UUID.randomUUID());
        conta.setEmail("moderacao@chefhome.test");
        when(usuarioRepository.findByEmailIgnoreCase("moderacao@chefhome.test")).thenReturn(Optional.of(conta));

        LoginResult result = loginService.loginGoogle(new GoogleUser("moderacao@chefhome.test", "Mod", null));

        assertEquals(TipoUsuario.MODERADOR, result.tipo());
        UsuarioAutenticado autenticado = tokenService.validarToken(result.accessToken()).orElseThrow();
        assertEquals(TipoUsuario.MODERADOR, autenticado.tipo());
        assertEquals(new ModeracaoRegistry("moderacao@chefhome.test").idDe("moderacao@chefhome.test"), autenticado.id());
    }

    @Test
    void registerComTipoModeradorDa400() {
        assertThrows(RegraDeNegocioException.class,
                () -> loginService.register(dto(TipoUsuario.MODERADOR, null), tokenRegistro()));
    }

    @Test
    void contaAtivaDeModerador() {
        UUID id = new ModeracaoRegistry("moderacao@chefhome.test").idDe("moderacao@chefhome.test");

        assertTrue(loginService.contaAtiva(new UsuarioAutenticado(id, "moderacao@chefhome.test", TipoUsuario.MODERADOR)));
        assertFalse(loginService.contaAtiva(new UsuarioAutenticado(id, "ex-moderador@x.com", TipoUsuario.MODERADOR)));
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
        RegisterResult result = loginService.register(dto(TipoUsuario.CLIENTE, List.of(enderecoValido())), tokenRegistro());

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
        loginService.register(dto(TipoUsuario.CLIENTE, List.of(enderecoValido())), tokenRegistro());

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

        loginService.register(dto(TipoUsuario.CLIENTE, List.of(enderecoValido())), token);

        assertNull(usuarioSalvo().getImageUrl());
    }

    @Test
    void registerComEmailJaExistenteEmQualquerTabelaLanca409() {
        when(chefeRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(new ChefeModel()));

        assertThrows(EmailJaCadastradoException.class,
                () -> loginService.register(dto(TipoUsuario.CLIENTE, List.of(enderecoValido())), tokenRegistro()));
        verify(usuarioRepository, never()).save(any());
        verify(chefeRepository, never()).save(any());
    }

    @Test
    void registerSemTokenOuComTokenDeAcessoLanca401() {
        String tokenDeAcesso = tokenService.gerarToken(new UsuarioAutenticado(UUID.randomUUID(), EMAIL, TipoUsuario.CLIENTE));

        assertThrows(RegistroTokenInvalidoException.class,
                () -> loginService.register(dto(TipoUsuario.CLIENTE, List.of(enderecoValido())), null));
        assertThrows(RegistroTokenInvalidoException.class,
                () -> loginService.register(dto(TipoUsuario.CLIENTE, List.of(enderecoValido())), tokenDeAcesso));
    }

    @Test
    void clienteSemEnderecoDa400() {
        assertThrows(RegraDeNegocioException.class,
                () -> loginService.register(dto(TipoUsuario.CLIENTE, null), tokenRegistro()));
        assertThrows(RegraDeNegocioException.class,
                () -> loginService.register(dto(TipoUsuario.CLIENTE, List.of()), tokenRegistro()));
        verify(usuarioRepository, never()).save(any());
    }

    private static com.couto.chefe_api.dto.EnderecoRequestDto enderecoValido() {
        return new com.couto.chefe_api.dto.EnderecoRequestDto("Rua A", "1", null, "01001-000", "São Paulo", "Centro", "SP",
                com.couto.chefe_api.enums.TipoDeResidencia.CASA);
    }

    private String tokenRegistro() {
        return tokenService.gerarTokenRegistro(googleUser);
    }

    private RegisterRequestDto dto(TipoUsuario tipo, List<com.couto.chefe_api.dto.EnderecoRequestDto> enderecos) {
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
