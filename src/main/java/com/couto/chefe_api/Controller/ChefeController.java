package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.ChefeRequestDto;
import com.couto.chefe_api.Dtos.ChefeResponseDto;
import com.couto.chefe_api.Services.ChefeService;
import jakarta.validation.Valid;
import com.couto.chefe_api.Security.AuthCookies;
import com.couto.chefe_api.Security.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chefe")
public class ChefeController {

    private final ChefeService service;
    private final AuthCookies authCookies;

    @GetMapping
    public ResponseEntity <Page<ChefeResponseDto>> listarChefes(Pageable pageable,@RequestParam(required = false) BigDecimal valorMinimo,@RequestParam(required = false) BigDecimal valorMaximo){
        return ResponseEntity.ok().body(service.mostrarChefes(pageable,valorMinimo,valorMaximo));
    }

    @PatchMapping("/me")
    public ResponseEntity<ChefeResponseDto> atualizar(@Valid @RequestBody ChefeRequestDto dto,
                                                      @AuthenticationPrincipal UsuarioAutenticado chefe) {
        return ResponseEntity.ok(service.atualizarDados(dto, chefe.id()));
    }

    @PatchMapping("/me/inativar")
    public ResponseEntity<Void> inativar(@AuthenticationPrincipal UsuarioAutenticado chefe) {
        service.inativar(chefe.id());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/ativar")
    public ResponseEntity<Void> ativar(@AuthenticationPrincipal UsuarioAutenticado chefe) {
        service.ativar(chefe.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/destaque")
    public ResponseEntity<Page<ChefeResponseDto>> chefesMaisAgendados(Pageable pageable) {
        return ResponseEntity.ok(service.chefesMaisAgendados(pageable));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deletar(@AuthenticationPrincipal UsuarioAutenticado chefe, HttpServletResponse response) {
        service.deletar(chefe.id());
        authCookies.adicionar(response, authCookies.expirar(AuthCookies.ACCESS_TOKEN, AuthCookies.PATH_RAIZ));
        return ResponseEntity.noContent().build();
    }
}
