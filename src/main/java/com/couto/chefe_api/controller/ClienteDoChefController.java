package com.couto.chefe_api.controller;

import com.couto.chefe_api.dto.ClienteDoChefDto;
import com.couto.chefe_api.dto.HistoricoClienteDto;
import com.couto.chefe_api.dto.NotaClienteDto;
import com.couto.chefe_api.dto.NotaClienteRequestDto;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.service.ClienteDoChefService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteDoChefController {

    private final ClienteDoChefService service;

    @GetMapping
    public ResponseEntity<Page<ClienteDoChefDto>> listar(@PageableDefault(size = 20) Pageable pageable,
                                                         @AuthenticationPrincipal UsuarioAutenticado chef) {
        return ResponseEntity.ok(service.listar(chef, pageable));
    }

    @GetMapping("/{id}/historico")
    public ResponseEntity<HistoricoClienteDto> historico(@PathVariable UUID id,
                                                         @AuthenticationPrincipal UsuarioAutenticado chef) {
        return ResponseEntity.ok(service.historico(id, chef));
    }

    /** 200 com a nota como ficou; texto vazio apaga e responde 204. */
    @PutMapping("/{id}/nota")
    public ResponseEntity<NotaClienteDto> nota(@PathVariable UUID id, @Valid @RequestBody NotaClienteRequestDto dto,
                                               @AuthenticationPrincipal UsuarioAutenticado chef) {
        return service.gravarNota(id, dto.texto(), chef)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
