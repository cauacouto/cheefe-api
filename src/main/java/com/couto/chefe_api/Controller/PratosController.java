package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.CadastraPratosDtoRequest;
import com.couto.chefe_api.Dtos.CadastratPratosDtoResponse;
import com.couto.chefe_api.Dtos.ListarPratosChefes;
import com.couto.chefe_api.Services.PratosService;
import jakarta.validation.Valid;
import com.couto.chefe_api.Security.UsuarioAutenticado;
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
    public ResponseEntity<CadastratPratosDtoResponse> cadastroPratos(@Valid @RequestPart("dados") CadastraPratosDtoRequest dto,
                                                                     @RequestParam MultipartFile foto,
                                                                     @AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pratosService.cadastrarPratos(dto, foto, chefe.id()));
    }

    @GetMapping("/chefe/{chefeId}")
    public ResponseEntity<List<ListarPratosChefes>> listaPorChefe(@PathVariable UUID chefeId){
        return ResponseEntity.ok(pratosService.listaPratos(chefeId));
    }

    @GetMapping
    public ResponseEntity<List<ListarPratosChefes>> listarTodos() {
        return ResponseEntity.ok(pratosService.listaPratos(null));
    }

    @GetMapping("/meus")
    public ResponseEntity<List<ListarPratosChefes>> meusPratos(@AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(pratosService.listaPratos(chefe.id()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CadastratPratosDtoResponse> updatePratos(@Valid @RequestBody CadastraPratosDtoRequest dto,
                                                                   @PathVariable Integer id,
                                                                   @AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(pratosService.updatePratos(dto, id, chefe.id()));
    }
}
