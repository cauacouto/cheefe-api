package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.AgendamentoResponseDto;
import com.couto.chefe_api.Dtos.AgendamentoResquestDto;
import com.couto.chefe_api.Dtos.ListaAgendamentoChefeDto;
import com.couto.chefe_api.Dtos.ListaAgendamentoUsuarioDto;
import com.couto.chefe_api.Services.agendamentoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/agendamentos")
public class AgendamentoController {

    private final agendamentoService service;

    public AgendamentoController(agendamentoService service) {
        this.service = service;
    }
@PostMapping
    public ResponseEntity<AgendamentoResponseDto> criarAgendamento(@RequestBody AgendamentoResquestDto dto,@RequestParam String email){
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarAgendamento(dto,email));
    }

    @GetMapping("/chefe/{chefeId}")
    public ResponseEntity<List<ListaAgendamentoChefeDto>> listarAgendamentoChefes(@PathVariable UUID chefeId){
        return ResponseEntity.ok(service.listarAgendamentosChefe(chefeId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgendamentoResponseDto> atualizarAgendamento(@RequestBody AgendamentoResquestDto dto,@PathVariable Long id){
        var update = service.atualizarAgedamento(dto, id);
        return ResponseEntity.ok().body(update);
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<ListaAgendamentoUsuarioDto>> listarAgedamentoUsuarios(@PathVariable UUID usuarioId){
     return ResponseEntity.ok(service.listarAgendamentosUsuario(usuarioId));
    }

    @PatchMapping("{id}/confirmar")
    public ResponseEntity<Void> confirmarAgendamento(@PathVariable Long id){
     service.confirmarAgendamento(id);
     return ResponseEntity.ok().build();
    }
}
