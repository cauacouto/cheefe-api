package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.*;
import com.couto.chefe_api.Security.AuthCookies;
import com.couto.chefe_api.Security.LoginService;
import com.couto.chefe_api.Security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> register(
            @Valid @RequestPart("dados") RegisterRequestDto dto,
            @RequestParam(value = "foto", required = false) MultipartFile foto,
            @CookieValue(name = AuthCookies.REGISTRO_TOKEN, required = false) String registroToken,
            HttpServletResponse response) {
        RegisterResult result = loginService.register(dto, foto, registroToken);
        authCookies.adicionar(response, authCookies.accessToken(result.accessToken()));
        authCookies.adicionar(response, authCookies.expirar(AuthCookies.REGISTRO_TOKEN, AuthCookies.PATH_REGISTRO));
        return ResponseEntity.status(HttpStatus.CREATED).body(result.corpo());
    }

    @GetMapping("/me")
    public ResponseEntity<SessaoDto> me(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(new SessaoDto(usuario.id(), usuario.email(), usuario.tipo()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        authCookies.adicionar(response, authCookies.expirar(AuthCookies.ACCESS_TOKEN, AuthCookies.PATH_RAIZ));
        authCookies.adicionar(response, authCookies.expirar(AuthCookies.REGISTRO_TOKEN, AuthCookies.PATH_REGISTRO));
        return ResponseEntity.ok().build();
    }
}
