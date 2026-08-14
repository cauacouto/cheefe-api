package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.*;
import com.couto.chefe_api.Security.LoginService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;




    @PostMapping(value = "/register",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> register(@RequestPart("dados") RegisterRequestDto dto, @RequestParam(value = "foto",required = false) MultipartFile foto){
       return switch (dto.getTipoUsuario()){
           case CLIENTE -> ResponseEntity.status(HttpStatus.CREATED).body(loginService.RegisterUsuario(dto,foto));
           case CHEFE ->  ResponseEntity.status(HttpStatus.CREATED).body(loginService.registerChefe(dto,foto));
       };
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody DadosLoginDto dto, HttpServletResponse response){
        var result = loginService.login(dto);

        if (result.token() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, createAccessTokenCookie(result.token(), 8 * 3600).toString());
        }

        return ResponseEntity.ok().body(result.responseDto());
    }

    @PostMapping("/request-otp")
    public ResponseEntity<OtpResponseDto> solicitarOtp(@RequestBody SolicitarOtpDto dto){
        String otpId = this.loginService.solicitarOtp(dto.email());
        return ResponseEntity.ok(new OtpResponseDto(otpId));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, createAccessTokenCookie("", 0).toString());
        return ResponseEntity.ok().build();
    }

    private ResponseCookie createAccessTokenCookie(String token, long maxAge) {
        return ResponseCookie.from("accessToken", token)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
    }
}
