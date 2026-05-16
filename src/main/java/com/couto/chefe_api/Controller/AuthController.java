package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.DadosLoginDto;
import com.couto.chefe_api.Dtos.DadosTokenDto;
import com.couto.chefe_api.Dtos.UserRequestDto;
import com.couto.chefe_api.Dtos.UserResponseDto;
import com.couto.chefe_api.Security.LoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;


    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(@RequestBody UserRequestDto request){
        UserResponseDto response = loginService.Register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<DadosTokenDto> login(@RequestBody DadosLoginDto dto){
    var token = loginService.login(dto);
    return ResponseEntity.ok(new DadosTokenDto(token));
    }
}
