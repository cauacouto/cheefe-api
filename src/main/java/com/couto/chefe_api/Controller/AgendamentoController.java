package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.AgendamentoResponseDto;
import com.couto.chefe_api.Dtos.AgendamentoResquestDto;
import com.couto.chefe_api.Services.agendamentoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agendamento")
public class AgendamentoController {

    private final agendamentoService service;

    public AgendamentoController(agendamentoService service) {
        this.service = service;
    }
@PostMapping
    public ResponseEntity<AgendamentoResponseDto> criarAgendamento(@RequestBody AgendamentoResquestDto dto,@RequestParam String email){
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarAgendamento(dto,email));
    }
}
