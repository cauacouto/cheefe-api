package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.ContagemDto;
import com.couto.chefe_api.Dtos.NotificacaoDto;
import com.couto.chefe_api.Security.UsuarioAutenticado;
import com.couto.chefe_api.Services.NotificacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

    private final NotificacaoService service;

    @GetMapping
    public ResponseEntity<Page<NotificacaoDto>> listar(@RequestParam(defaultValue = "false") boolean somenteNaoLidas,
                                                       Pageable pageable,
                                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(service.listar(usuario.id(), somenteNaoLidas, pageable));
    }

    @GetMapping("/nao-lidas/contagem")
    public ResponseEntity<ContagemDto> contar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(new ContagemDto(service.contarNaoLidas(usuario.id())));
    }

    @PatchMapping("/{id}/lida")
    public ResponseEntity<Void> marcarLida(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.marcarLida(id, usuario.id());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/lidas")
    public ResponseEntity<Void> marcarTodasLidas(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.marcarTodasLidas(usuario.id());
        return ResponseEntity.noContent().build();
    }
}
