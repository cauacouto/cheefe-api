package com.couto.chefe_api.controller;

import com.couto.chefe_api.dto.DenunciaCriadaDto;
import com.couto.chefe_api.dto.DenunciaRequestDto;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.service.DenunciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/denuncias")
@RequiredArgsConstructor
public class DenunciaController {

    private final DenunciaService service;

    @PostMapping
    public ResponseEntity<DenunciaCriadaDto> criar(@Valid @RequestBody DenunciaRequestDto dto,
                                                   @AuthenticationPrincipal UsuarioAutenticado cliente) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto, cliente.id()));
    }
}
