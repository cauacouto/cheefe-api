package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.CadastraPratosDtoRequest;
import com.couto.chefe_api.Dtos.CadastratPratosDtoResponse;
import com.couto.chefe_api.Dtos.ListarPratosChefes;
import com.couto.chefe_api.Excepitons.ChefeEception;
import com.couto.chefe_api.Services.PratosService;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.repositorys.ChefeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pratos")
public class PratosController {

   private final PratosService pratosService;
   private  final ChefeRepository chefeRepository;


                                                                                                                                                  //adicionar UserDeails
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CadastratPratosDtoResponse> cadastroPratos(@RequestPart("dados") CadastraPratosDtoRequest dto, @RequestParam MultipartFile foto, @AuthenticationPrincipal UserDetails usuarioLogado){

        ChefeModel chefe = chefeRepository.findByEmail(usuarioLogado.getUsername())
                .orElseThrow(ChefeEception::new);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pratosService.cadastrarPratos(dto, foto, chefe.getId()));
    }


    @GetMapping("/chefe/{chefeId}")
    public ResponseEntity<List<ListarPratosChefes>> listaPorChefe(@PathVariable UUID chefeId){
        return ResponseEntity.ok(pratosService.listaPratos(chefeId));
    }

    @GetMapping
    public ResponseEntity<List<ListarPratosChefes>> listarTodos() {
        return ResponseEntity.ok(pratosService.listaPratos(null));
    }


    @GetMapping("/pratos/meus-pratos")
    public ResponseEntity<List<ListarPratosChefes>> meusPratos(@AuthenticationPrincipal UserDetails usuarioLogado) {
     ChefeModel chefe = chefeRepository.findByEmail(usuarioLogado.getUsername())
             .orElseThrow(ChefeEception::new);
        return ResponseEntity.ok(pratosService.listaPratos(chefe.getId()));
    }

    @PutMapping
    public ResponseEntity<CadastratPratosDtoResponse> updatePratos(@RequestBody CadastraPratosDtoRequest dto, Integer id){
        CadastratPratosDtoResponse response = pratosService.updatePratos(dto,id);
        return ResponseEntity.ok().body(response);
    }


}
