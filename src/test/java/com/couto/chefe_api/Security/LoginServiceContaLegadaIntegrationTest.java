package com.couto.chefe_api.Security;

import com.couto.chefe_api.Dtos.LoginResult;
import com.couto.chefe_api.Dtos.RegisterRequestDto;
import com.couto.chefe_api.Excepitons.EmailJaCadastradoException;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.ChefeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contas criadas na época do OTP guardam o e-mail como foi digitado (ex.: "Ana@Gmail.com"),
 * enquanto o Google devolve o e-mail normalizado em minúsculas.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoginServiceContaLegadaIntegrationTest {

    @Autowired
    private LoginService loginService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ChefeRepository chefeRepository;

    @Test
    void clienteLegadoComMaiusculasEhEncontrado() {
        UserModel usuario = new UserModel();
        usuario.setNome("Ana");
        usuario.setEmail("Ana@Gmail.com");
        usuarioRepository.save(usuario);

        LoginResult result = loginService.loginGoogle(new GoogleUser("ana@gmail.com", "Ana", null));

        assertNull(result.registroToken());
        assertEquals(TipoUsuario.CLIENTE, tokenService.validarToken(result.accessToken()).orElseThrow().tipo());
    }

    @Test
    void chefLegadoComMaiusculasEhEncontrado() {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome("Bia");
        chefe.setEmail("Bia@Gmail.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefeRepository.save(chefe);

        LoginResult result = loginService.loginGoogle(new GoogleUser("bia@gmail.com", "Bia", null));

        assertNull(result.registroToken());
        assertEquals(TipoUsuario.CHEFE, tokenService.validarToken(result.accessToken()).orElseThrow().tipo());
    }

    @Test
    void cadastroNaoDuplicaContaLegadaComMaiusculas() {
        UserModel usuario = new UserModel();
        usuario.setNome("Ana");
        usuario.setEmail("Ana@Gmail.com");
        usuarioRepository.save(usuario);
        RegisterRequestDto dto = new RegisterRequestDto();
        dto.setTipoUsuario(TipoUsuario.CLIENTE);
        dto.setNome("Ana");
        dto.setEndereco(List.of());
        String token = tokenService.gerarTokenRegistro(new GoogleUser("ana@gmail.com", "Ana", null));

        assertThrows(EmailJaCadastradoException.class, () -> loginService.register(dto, null, token));
    }
}
