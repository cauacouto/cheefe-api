package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.DadosLoginDto;
import com.couto.chefe_api.Dtos.DadosTokenDto;
import com.couto.chefe_api.Dtos.RegisterRequestDto;
import com.couto.chefe_api.Security.LoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;



    @PostMapping(value = "/register",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> register(@RequestPart("dados") RegisterRequestDto dto, @RequestParam MultipartFile foto){
       return switch (dto.getTipoUsuario()){
           case CLIENTE -> ResponseEntity.status(HttpStatus.CREATED).body(loginService.RegisterUsuario(dto));
           case CHEFE ->  ResponseEntity.status(HttpStatus.CREATED).body(loginService.registerChefe(dto,foto));
       };
    }

    @PostMapping("/login")
    public ResponseEntity<DadosTokenDto> login(@RequestBody DadosLoginDto dto){
    var token = loginService.login(dto);
    return ResponseEntity.ok(new DadosTokenDto(token));
    }
}
