package com.couto.chefe_api.controller;

import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.couto.chefe_api.dto.AgendamentoResponseDto;
import com.couto.chefe_api.dto.AgendamentoResquestDto;
import com.couto.chefe_api.dto.AtualizarAgendamentoRequestDto;
import com.couto.chefe_api.dto.ListaAgendamentoChefeDto;
import com.couto.chefe_api.dto.ListaAgendamentoUsuarioDto;
import com.couto.chefe_api.service.agendamentoService;
import jakarta.validation.Valid;
import com.couto.chefe_api.security.UsuarioAutenticado;
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
    public ResponseEntity<Page<ListaAgendamentoUsuarioDto>> meus(@PageableDefault(size = 20) Pageable pageable,
                                                                 @AuthenticationPrincipal UsuarioAutenticado cliente) {
        return ResponseEntity.ok(service.listarAgendamentosUsuario(cliente.id(), pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgendamentoResponseDto> atualizarAgendamento(@Valid @RequestBody AtualizarAgendamentoRequestDto dto,
                                                                       @PathVariable Long id,
                                                                       @AuthenticationPrincipal UsuarioAutenticado cliente) {
        return ResponseEntity.ok(service.atualizarAgedamento(dto, id, cliente.id()));
    }

    @GetMapping("/recebidos")
    public ResponseEntity<Page<ListaAgendamentoChefeDto>> recebidos(@PageableDefault(size = 20) Pageable pageable,
                                                                    @AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(service.listarAgendamentosChefe(chefe.id(), pageable));
    }

    @PatchMapping("/{id}/confirmar")
    public ResponseEntity<Void> confirmarAgendamento(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado chefe) {
        service.confirmarAgendamento(id, chefe.id());
        return ResponseEntity.ok().build();
    }
}
