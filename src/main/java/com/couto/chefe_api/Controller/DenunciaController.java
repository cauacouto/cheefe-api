package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.DenunciaCriadaDto;
import com.couto.chefe_api.Dtos.DenunciaRequestDto;
import com.couto.chefe_api.Security.UsuarioAutenticado;
import com.couto.chefe_api.Services.DenunciaService;
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
