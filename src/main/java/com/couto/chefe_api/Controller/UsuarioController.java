package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.EnderecoRequestDto;
import com.couto.chefe_api.Dtos.EnderecoResponseDto;
import com.couto.chefe_api.Dtos.UserRequestDto;
import com.couto.chefe_api.Dtos.UserResponseDto;
import com.couto.chefe_api.Security.AuthCookies;
import com.couto.chefe_api.Security.UsuarioAutenticado;
import com.couto.chefe_api.Services.EnderecoService;
import jakarta.servlet.http.HttpServletResponse;
import com.couto.chefe_api.User.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/usuario")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final EnderecoService enderecoService;
    private final AuthCookies authCookies;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> buscar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(usuarioService.buscar(usuario.id()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDto> atualizar(@Valid @RequestBody UserRequestDto dto,
                                                     @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(usuarioService.atualizar(dto, usuario.id()));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deletar(@AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletResponse response) {
        usuarioService.deletar(usuario.id());
        authCookies.adicionar(response, authCookies.expirar(AuthCookies.ACCESS_TOKEN, AuthCookies.PATH_RAIZ));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/enderecos")
    public ResponseEntity<List<EnderecoResponseDto>> listarEnderecos(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(enderecoService.listarEnderecos(usuario.id()));
    }

    @PostMapping("/enderecos")
    public ResponseEntity<EnderecoResponseDto> adicionarEndereco(
            @Valid @RequestBody EnderecoRequestDto dto,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enderecoService.adicionarEndereco(dto, usuario.id()));
    }
}
