package com.couto.chefe_api.Controller;

import com.couto.chefe_api.Dtos.UserRequestDto;
import com.couto.chefe_api.Dtos.UserResponseDto;
import com.couto.chefe_api.User.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> buscaPorId(@PathVariable UUID id){
        var buscarId = usuarioService.buscarPorId(id);
        return ResponseEntity.ok().body(buscarId);
    }

    @GetMapping
    public ResponseEntity<Page<UserResponseDto>> listarUsuarios(Pageable page){
     Page<UserResponseDto> users =  usuarioService.listarUsuarios(page);
     return ResponseEntity.ok().body(users);

    }
    @DeleteMapping("/{id}")
public ResponseEntity<Void> deletar (@PathVariable UUID id){
        usuarioService.deletar(id);
        return ResponseEntity.noContent().build();
}

}
