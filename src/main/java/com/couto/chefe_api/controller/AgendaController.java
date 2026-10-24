package com.couto.chefe_api.controller;

import com.couto.chefe_api.dto.AgendaChefeDto;
import com.couto.chefe_api.dto.BloqueioAgendaDto;
import com.couto.chefe_api.dto.BloqueioAgendaRequestDto;
import com.couto.chefe_api.dto.DiasFixosRequestDto;
import com.couto.chefe_api.dto.PeriodoIndisponivelDto;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.service.AgendaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AgendaController {

    private static final String DIA = "dd-MM-yyyy";

    private final AgendaService service;

    @GetMapping("/chefe/me/agenda")
    public ResponseEntity<AgendaChefeDto> minhaAgenda(@AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(service.agenda(chefe.id()));
    }

    @PostMapping("/chefe/me/agenda/bloqueios")
    public ResponseEntity<BloqueioAgendaDto> bloquear(@Valid @RequestBody BloqueioAgendaRequestDto dto,
                                                      @AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarBloqueio(chefe.id(), dto));
    }

    @DeleteMapping("/chefe/me/agenda/bloqueios/{id}")
    public ResponseEntity<Void> desbloquear(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado chefe) {
        service.removerBloqueio(chefe.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/chefe/me/agenda/dias-fixos")
    public ResponseEntity<List<DayOfWeek>> diasFixos(@Valid @RequestBody DiasFixosRequestDto dto,
                                                     @AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(service.definirDiasFixos(chefe.id(), dto.dias()));
    }

    @GetMapping("/chefe/{id}/agenda")
    public ResponseEntity<List<PeriodoIndisponivelDto>> consulta(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(pattern = DIA) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(pattern = DIA) LocalDate ate) {
        return ResponseEntity.ok(service.consultaPublica(id, de, ate));
    }
}
