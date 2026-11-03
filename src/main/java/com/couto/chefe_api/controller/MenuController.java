package com.couto.chefe_api.controller;

import com.couto.chefe_api.dto.MenuRequestDto;
import com.couto.chefe_api.dto.MenuResponseDto;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MenuController {

    private final MenuService service;

    @PostMapping("/menus")
    public ResponseEntity<MenuResponseDto> criar(@Valid @RequestBody MenuRequestDto dto,
                                                 @AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto, chefe.id()));
    }

    @PutMapping("/menus/{id}")
    public ResponseEntity<MenuResponseDto> editar(@PathVariable Long id, @Valid @RequestBody MenuRequestDto dto,
                                                  @AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(service.editar(id, dto, chefe.id()));
    }

    @PatchMapping("/menus/{id}/pausar")
    public ResponseEntity<MenuResponseDto> pausar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(service.pausar(id, chefe.id()));
    }

    @PatchMapping("/menus/{id}/ativar")
    public ResponseEntity<MenuResponseDto> ativar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(service.ativar(id, chefe.id()));
    }

    @GetMapping("/menus/meus")
    public ResponseEntity<List<MenuResponseDto>> meus(@AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(service.meus(chefe.id()));
    }

    @GetMapping("/chefe/{id}/menus")
    public ResponseEntity<List<MenuResponseDto>> doChefe(@PathVariable UUID id) {
        return ResponseEntity.ok(service.doChefe(id));
    }
}
