package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.ChefeRequestDto;
import com.couto.chefe_api.Dtos.ChefeResponseDto;
import com.couto.chefe_api.Services.ChefeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
