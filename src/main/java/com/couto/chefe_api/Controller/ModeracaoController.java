package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.DenunciaModeracaoDto;
import com.couto.chefe_api.Security.UsuarioAutenticado;
import com.couto.chefe_api.Services.ModeracaoService;
import com.couto.chefe_api.enums.StatusDenuncia;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Área de moderação; para quem não é moderador ela responde 404 (ver AcessoNegadoHandler). */
@RestController
@RequestMapping("/moderacao")
@RequiredArgsConstructor
public class ModeracaoController {

    private final ModeracaoService service;

    @GetMapping("/denuncias")
    public ResponseEntity<Page<DenunciaModeracaoDto>> fila(@RequestParam(defaultValue = "ABERTA") StatusDenuncia status,
                                                           Pageable pageable) {
        return ResponseEntity.ok(service.listar(status, pageable));
    }

    @PatchMapping("/denuncias/{id}/arquivar")
    public ResponseEntity<Void> arquivar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado moderador) {
        service.arquivar(id, moderador.email());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/chefes/{id}/suspender")
    public ResponseEntity<Void> suspender(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado moderador) {
        service.suspender(id, moderador.email());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/chefes/{id}/reativar")
    public ResponseEntity<Void> reativar(@PathVariable UUID id) {
        service.reativar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/chefes/{id}/banir")
    public ResponseEntity<Void> banir(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado moderador) {
        service.banir(id, moderador.email());
        return ResponseEntity.noContent().build();
    }
}
