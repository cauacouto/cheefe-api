package com.couto.chefe_api.controller;

import com.couto.chefe_api.dto.AvaliacaoPendenteDto;
import com.couto.chefe_api.dto.AvaliarRequestDto;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.service.AvaliacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AvaliacaoController {

    private final AvaliacaoService service;

    @PostMapping("/agendamentos/{id}/avaliacoes")
    public ResponseEntity<Map<String, Long>> avaliar(@PathVariable Long id, @Valid @RequestBody AvaliarRequestDto dto,
                                                     @AuthenticationPrincipal UsuarioAutenticado autor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", service.avaliar(id, autor, dto)));
    }

    @GetMapping("/agendamentos/{id}/avaliacoes/pendentes")
    public ResponseEntity<List<AvaliacaoPendenteDto>> pendentes(@PathVariable Long id,
                                                                @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(service.pendentes(id, usuario));
    }
}
