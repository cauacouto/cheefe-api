package com.couto.chefe_api.controller;

import com.couto.chefe_api.dto.PedirCodigoTelefoneDto;
import com.couto.chefe_api.dto.TelefoneVerificadoDto;
import com.couto.chefe_api.dto.VerificarTelefoneDto;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.service.TelefoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Verificação do telefone por SMS da conta logada (cliente ou chef). */
@RestController
@RequestMapping("/telefone")
@RequiredArgsConstructor
public class TelefoneController {

    private final TelefoneService service;

    @PostMapping("/codigo")
    public ResponseEntity<Void> pedirCodigo(@RequestBody PedirCodigoTelefoneDto dto,
                                            @AuthenticationPrincipal UsuarioAutenticado conta) {
        service.pedirCodigo(conta, dto.telefone());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/verificar")
    public ResponseEntity<TelefoneVerificadoDto> verificar(@RequestBody VerificarTelefoneDto dto,
                                                           @AuthenticationPrincipal UsuarioAutenticado conta) {
        return ResponseEntity.ok(service.verificar(conta, dto.telefone(), dto.codigo()));
    }
}
