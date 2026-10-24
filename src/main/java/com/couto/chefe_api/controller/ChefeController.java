package com.couto.chefe_api.controller;

import com.couto.chefe_api.enums.Especialidades;

import com.couto.chefe_api.dto.ChefePerfilDto;
import com.couto.chefe_api.dto.FotoPerfilDto;
import com.couto.chefe_api.service.FotoPerfilService;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import com.couto.chefe_api.dto.ChefeRequestDto;
import com.couto.chefe_api.dto.ChefeResponseDto;
import com.couto.chefe_api.service.ChefeService;
import jakarta.validation.Valid;
import com.couto.chefe_api.security.AuthCookies;
import com.couto.chefe_api.security.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chefe")
public class ChefeController {

    private final ChefeService service;
    private final AuthCookies authCookies;
    private final FotoPerfilService fotoPerfilService;

    @GetMapping
    public ResponseEntity<Page<ChefeResponseDto>> listarChefes(Pageable pageable,
                                                               @RequestParam(required = false) BigDecimal valorMinimo,
                                                               @RequestParam(required = false) BigDecimal valorMaximo,
                                                               @RequestParam(required = false) String cidade,
                                                               @RequestParam(required = false) String estado,
                                                               @RequestParam(required = false) Especialidades especialidade) {
        return ResponseEntity.ok().body(service.mostrarChefes(pageable, valorMinimo, valorMaximo, cidade, estado, especialidade));
    }

    @GetMapping("/me")
    public ResponseEntity<ChefePerfilDto> perfil(@AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(service.buscarPerfil(chefe.id()));
    }

    @PatchMapping("/me")
    public ResponseEntity<ChefeResponseDto> atualizar(@Valid @RequestBody ChefeRequestDto dto,
                                                      @AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(service.atualizarDados(dto, chefe.id()));
    }

    @PatchMapping("/me/inativar")
    public ResponseEntity<Void> inativar(@AuthenticationPrincipal UsuarioAutenticado chefe) {
        service.inativar(chefe.id());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/ativar")
    public ResponseEntity<Void> ativar(@AuthenticationPrincipal UsuarioAutenticado chefe) {
        service.ativar(chefe.id());
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/me/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FotoPerfilDto> trocarFoto(@RequestParam MultipartFile foto,
                                                    @AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(new FotoPerfilDto(fotoPerfilService.trocarFotoChefe(chefe.id(), foto)));
    }

    @GetMapping("/destaque")
    public ResponseEntity<Page<ChefeResponseDto>> chefesMaisAgendados(Pageable pageable) {
        return ResponseEntity.ok(service.chefesMaisAgendados(pageable));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deletar(@AuthenticationPrincipal UsuarioAutenticado chefe, HttpServletResponse response) {
        service.deletar(chefe.id());
        authCookies.adicionar(response, authCookies.expirar(AuthCookies.ACCESS_TOKEN, AuthCookies.PATH_RAIZ));
        return ResponseEntity.noContent().build();
    }
}
