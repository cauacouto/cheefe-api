package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.AbrirConversaDto;
import com.couto.chefe_api.Dtos.ConversaDto;
import com.couto.chefe_api.Dtos.DenunciaCriadaDto;
import com.couto.chefe_api.Dtos.DenunciaMensagemRequestDto;
import com.couto.chefe_api.Dtos.EnviarMensagemDto;
import com.couto.chefe_api.Dtos.MensagemDto;
import com.couto.chefe_api.Security.UsuarioAutenticado;
import com.couto.chefe_api.Services.ChatService;
import com.couto.chefe_api.Services.DenunciaMensagemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/conversas")
@RequiredArgsConstructor
public class ConversaController {

    private final ChatService service;
    private final DenunciaMensagemService denunciaService;

    @PostMapping
    public ResponseEntity<ConversaDto> abrir(@Valid @RequestBody AbrirConversaDto dto,
                                             @AuthenticationPrincipal UsuarioAutenticado cliente) {
        ChatService.ConversaAberta aberta = service.abrirConversa(cliente.id(), dto.chefeId());
        return ResponseEntity.status(aberta.criada() ? HttpStatus.CREATED : HttpStatus.OK).body(aberta.conversa());
    }

    @GetMapping
    public ResponseEntity<Page<ConversaDto>> listar(@PageableDefault(size = 20) Pageable pageable,
                                                    @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(service.listarConversas(usuario, pageable));
    }

    @GetMapping("/{id}/mensagens")
    public ResponseEntity<List<MensagemDto>> mensagens(@PathVariable Long id,
                                                       @RequestParam(required = false) Long antesDe,
                                                       @RequestParam(defaultValue = "30") int size,
                                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(service.listarMensagens(id, usuario, antesDe, size));
    }

    @PostMapping("/{id}/mensagens")
    public ResponseEntity<MensagemDto> enviar(@PathVariable Long id, @Valid @RequestBody EnviarMensagemDto dto,
                                              @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.enviar(id, usuario, dto.texto()));
    }

    @PatchMapping("/{id}/mensagens/{mensagemId}")
    public ResponseEntity<MensagemDto> editar(@PathVariable Long id, @PathVariable Long mensagemId,
                                              @Valid @RequestBody EnviarMensagemDto dto,
                                              @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(service.editar(id, mensagemId, usuario, dto.texto()));
    }

    @DeleteMapping("/{id}/mensagens/{mensagemId}")
    public ResponseEntity<Void> apagar(@PathVariable Long id, @PathVariable Long mensagemId,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.apagar(id, mensagemId, usuario);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/mensagens/{mensagemId}/denuncias")
    public ResponseEntity<DenunciaCriadaDto> denunciar(@PathVariable Long id, @PathVariable Long mensagemId,
                                                       @Valid @RequestBody DenunciaMensagemRequestDto dto,
                                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(denunciaService.denunciar(id, mensagemId, usuario, dto));
    }

    @PatchMapping("/{id}/lidas")
    public ResponseEntity<Void> marcarLidas(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.marcarLidas(id, usuario);
        return ResponseEntity.noContent().build();
    }
}
