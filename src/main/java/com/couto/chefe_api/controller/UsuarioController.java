package com.couto.chefe_api.controller;

import com.couto.chefe_api.dto.EnderecoRequestDto;
import com.couto.chefe_api.dto.FotoPerfilDto;
import com.couto.chefe_api.service.FotoPerfilService;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import com.couto.chefe_api.dto.EnderecoResponseDto;
import com.couto.chefe_api.dto.UserRequestDto;
import com.couto.chefe_api.dto.UserResponseDto;
import com.couto.chefe_api.security.AuthCookies;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.service.EnderecoService;
import jakarta.servlet.http.HttpServletResponse;
import com.couto.chefe_api.service.UsuarioService;
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
    private final FotoPerfilService fotoPerfilService;

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

    @PutMapping(value = "/me/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FotoPerfilDto> trocarFoto(@RequestParam MultipartFile foto,
                                                    @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(new FotoPerfilDto(fotoPerfilService.trocarFotoUsuario(usuario.id(), foto)));
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
