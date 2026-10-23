package com.couto.chefe_api.controller;

import com.couto.chefe_api.dto.AcaoModeracaoDto;
import com.couto.chefe_api.dto.BloqueioContaDto;
import com.couto.chefe_api.enums.StatusBloqueio;
import com.couto.chefe_api.dto.DenunciaMensagemModeracaoDto;
import com.couto.chefe_api.dto.DenunciaModeracaoDto;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.service.ModeracaoService;
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

    @GetMapping("/acoes")
    public ResponseEntity<Page<AcaoModeracaoDto>> historico(Pageable pageable) {
        return ResponseEntity.ok(service.historico(pageable));
    }

    @PatchMapping("/denuncias/{id}/arquivar")
    public ResponseEntity<Void> arquivar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado moderador) {
        service.arquivar(id, moderador.email());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/denuncias-mensagens")
    public ResponseEntity<Page<DenunciaMensagemModeracaoDto>> filaDeMensagens(
            @RequestParam(defaultValue = "ABERTA") StatusDenuncia status, Pageable pageable) {
        return ResponseEntity.ok(service.listarDenunciasMensagens(status, pageable));
    }

    @PatchMapping("/denuncias-mensagens/{id}/arquivar")
    public ResponseEntity<Void> arquivarDenunciaMensagem(@PathVariable Long id,
                                                         @AuthenticationPrincipal UsuarioAutenticado moderador) {
        service.arquivarDenunciaMensagem(id, moderador.email());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/denuncias-mensagens/{id}/remover-mensagem")
    public ResponseEntity<Void> removerMensagem(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado moderador) {
        service.removerMensagem(id, moderador.email());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/bloqueios")
    public ResponseEntity<Page<BloqueioContaDto>> filaDeBloqueios(
            @RequestParam(defaultValue = "ABERTO") StatusBloqueio status, Pageable pageable) {
        return ResponseEntity.ok(service.listarBloqueios(status, pageable));
    }

    @PatchMapping("/bloqueios/{id}/confirmar")
    public ResponseEntity<Void> confirmarBloqueio(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado moderador) {
        service.confirmarBloqueio(id, moderador.email());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/bloqueios/{id}/reverter")
    public ResponseEntity<Void> reverterBloqueio(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado moderador) {
        service.reverterBloqueio(id, moderador.email());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/chefes/{id}/suspender")
    public ResponseEntity<Void> suspender(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado moderador) {
        service.suspender(id, moderador.email());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/chefes/{id}/reativar")
    public ResponseEntity<Void> reativar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado moderador) {
        service.reativar(id, moderador.email());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/chefes/{id}/banir")
    public ResponseEntity<Void> banir(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado moderador) {
        service.banir(id, moderador.email());
        return ResponseEntity.noContent().build();
    }
}
