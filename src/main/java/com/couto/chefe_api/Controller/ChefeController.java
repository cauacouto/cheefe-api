package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.ChefeRequestDto;
import com.couto.chefe_api.Dtos.ChefeResponseDto;
import com.couto.chefe_api.Services.ChefeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chefe")
public class ChefeController {

    private final ChefeService service;

    @GetMapping
    public ResponseEntity <Page<ChefeResponseDto>> listarChefes(Pageable pageable,@RequestParam(required = false) BigDecimal valorMinimo,@RequestParam(required = false) BigDecimal valorMaximo){
        return ResponseEntity.ok().body(service.mostrarChefes(pageable,valorMinimo,valorMaximo));
    }
    @PatchMapping("/{id}")
    public ResponseEntity<ChefeResponseDto> atualizar(@RequestBody ChefeRequestDto dto, @PathVariable UUID id){
        var chefeAtualizado = service.atualizarDados(dto,id);
        return ResponseEntity.ok(chefeAtualizado);

    }
    @PatchMapping("/{id}/inativar")
    public ResponseEntity<Void> iniativar(@PathVariable UUID id){
        service.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/destaque")
    public ResponseEntity<Page<ChefeResponseDto>> chefesMaisAgendados(Pageable pageable) {
        return ResponseEntity.ok(service.chefesMaisAgendados(pageable));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id){
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }

}
