package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.UserRequestDto;
import com.couto.chefe_api.Dtos.UserResponseDto;
import com.couto.chefe_api.User.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/usuario")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;


    @PutMapping
    public ResponseEntity<UserResponseDto> atualizar(@RequestBody UserRequestDto dto, UUID id){
        var update = usuarioService.atualizarUsuario(dto,id);
        return ResponseEntity.ok().body(update);
    }


}
