package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.ChefeRequestDto;
import com.couto.chefe_api.Dtos.ChefeResponseDto;
import com.couto.chefe_api.Services.ChefeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/chefe")
public class ChefeController {

    private final ChefeService service;

    public ChefeController(ChefeService service) {
        this.service = service;
    }
    @PostMapping
    public ResponseEntity<ChefeResponseDto> salvarChefe(@RequestBody @Validated ChefeRequestDto dto){
        return ResponseEntity.status(HttpStatus.CREATED).body(service.salvarChefe(dto));
    }
    @GetMapping
    public ResponseEntity <Page<ChefeResponseDto>> listarChefes(Pageable pageable){
        return ResponseEntity.ok().body(service.mostrarChefes(pageable));
    }
    @PatchMapping("/{id}")
    public ResponseEntity<ChefeResponseDto> atualizar(@RequestBody ChefeRequestDto dto, @PathVariable UUID id){
        var chefeAtualizado = service.atualizarDados(dto,id);
        return ResponseEntity.ok(chefeAtualizado);

    }
    @PutMapping("{id}/inativar")
    public ResponseEntity<Void> iniativar(@PathVariable UUID id){
        service.inativar(id);
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id){
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }

}
