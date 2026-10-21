package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.AgendamentoResponseDto;
import com.couto.chefe_api.Dtos.AgendamentoResquestDto;
import com.couto.chefe_api.Dtos.AtualizarAgendamentoRequestDto;
import com.couto.chefe_api.Dtos.ListaAgendamentoChefeDto;
import com.couto.chefe_api.Dtos.ListaAgendamentoUsuarioDto;
import com.couto.chefe_api.Services.agendamentoService;
import jakarta.validation.Valid;
import com.couto.chefe_api.Security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/agendamentos")
@RequiredArgsConstructor
public class AgendamentoController {

    private final agendamentoService service;

    @PostMapping
    public ResponseEntity<AgendamentoResponseDto> criarAgendamento(@Valid @RequestBody AgendamentoResquestDto dto,
                                                                   @AuthenticationPrincipal UsuarioAutenticado cliente) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarAgendamento(dto, cliente.id()));
    }

    @GetMapping("/meus")
    public ResponseEntity<List<ListaAgendamentoUsuarioDto>> meus(@AuthenticationPrincipal UsuarioAutenticado cliente) {
        return ResponseEntity.ok(service.listarAgendamentosUsuario(cliente.id()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgendamentoResponseDto> atualizarAgendamento(@Valid @RequestBody AtualizarAgendamentoRequestDto dto,
                                                                       @PathVariable Long id,
                                                                       @AuthenticationPrincipal UsuarioAutenticado cliente) {
        return ResponseEntity.ok(service.atualizarAgedamento(dto, id, cliente.id()));
    }

    @GetMapping("/recebidos")
    public ResponseEntity<List<ListaAgendamentoChefeDto>> recebidos(@AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(service.listarAgendamentosChefe(chefe.id()));
    }

    @PatchMapping("/{id}/confirmar")
    public ResponseEntity<Void> confirmarAgendamento(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado chefe) {
        service.confirmarAgendamento(id, chefe.id());
        return ResponseEntity.ok().build();
    }
}
