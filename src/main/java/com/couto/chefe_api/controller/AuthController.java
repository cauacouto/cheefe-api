package com.couto.chefe_api.controller;

import com.couto.chefe_api.dto.*;
import com.couto.chefe_api.security.AuthCookies;
import com.couto.chefe_api.security.LoginService;
import com.couto.chefe_api.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;
    private final AuthCookies authCookies;

    @GetMapping("/register")
    public ResponseEntity<RegistroPendenteDto> dadosRegistro(
            @CookieValue(name = AuthCookies.REGISTRO_TOKEN, required = false) String registroToken) {
        return ResponseEntity.ok(loginService.dadosRegistro(registroToken));
    }

    @PostMapping("/register")
    public ResponseEntity<Object> register(
            @Valid @RequestBody RegisterRequestDto dto,
            @CookieValue(name = AuthCookies.REGISTRO_TOKEN, required = false) String registroToken,
            HttpServletResponse response) {
        RegisterResult result = loginService.register(dto, registroToken);
        authCookies.adicionar(response, authCookies.accessToken(result.accessToken()));
        authCookies.adicionar(response, authCookies.expirar(AuthCookies.REGISTRO_TOKEN, AuthCookies.PATH_REGISTRO));
        return ResponseEntity.status(HttpStatus.CREATED).body(result.corpo());
    }

    @GetMapping("/me")
    public ResponseEntity<SessaoDto> me(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                        HttpServletResponse response) {
        if (!loginService.contaAtiva(usuario)) {
            // token ainda válido, mas a conta foi apagada: derruba a sessão
            authCookies.adicionar(response, authCookies.expirar(AuthCookies.ACCESS_TOKEN, AuthCookies.PATH_RAIZ));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new SessaoDto(usuario.id(), usuario.email(), usuario.tipo()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        authCookies.adicionar(response, authCookies.expirar(AuthCookies.ACCESS_TOKEN, AuthCookies.PATH_RAIZ));
        authCookies.adicionar(response, authCookies.expirar(AuthCookies.REGISTRO_TOKEN, AuthCookies.PATH_REGISTRO));
        return ResponseEntity.ok().build();
    }
}
