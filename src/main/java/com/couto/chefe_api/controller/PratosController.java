package com.couto.chefe_api.controller;

import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.couto.chefe_api.dto.CadastraPratosDtoRequest;
import com.couto.chefe_api.dto.CadastraPratosDtoResponse;
import com.couto.chefe_api.dto.ListarPratosChefes;
import com.couto.chefe_api.service.PratosService;
import jakarta.validation.Valid;
import com.couto.chefe_api.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pratos")
public class PratosController {

    private final PratosService pratosService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CadastraPratosDtoResponse> cadastroPratos(@Valid @RequestPart("dados") CadastraPratosDtoRequest dto,
                                                                     @RequestParam MultipartFile foto,
                                                                     @AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pratosService.cadastrarPratos(dto, foto, chefe.id()));
    }

    @GetMapping("/chefe/{chefeId}")
    public ResponseEntity<List<ListarPratosChefes>> listaPorChefe(@PathVariable UUID chefeId){
        return ResponseEntity.ok(pratosService.listarDoChefe(chefeId));
    }

    @GetMapping
    public ResponseEntity<Page<ListarPratosChefes>> listarTodos(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(pratosService.listarVisiveis(pageable));
    }

    @GetMapping("/meus")
    public ResponseEntity<List<ListarPratosChefes>> meusPratos(@AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(pratosService.listarMeus(chefe.id()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CadastraPratosDtoResponse> updatePratos(@Valid @RequestBody CadastraPratosDtoRequest dto,
                                                                   @PathVariable Integer id,
                                                                   @AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(pratosService.updatePratos(dto, id, chefe.id()));
    }
}
