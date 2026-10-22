package com.couto.chefe_api.security;

import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A conta do token está bloqueada? Uma consulta por chave primária; moderador vem da configuração, nunca do banco.
 */
@Component
@RequiredArgsConstructor
public class ContaBloqueioVerificador {

    private final UsuarioRepository usuarioRepository;
    private final ChefeRepository chefeRepository;

    public boolean bloqueada(UsuarioAutenticado usuario) {
        return switch (usuario.tipo()) {
            case CLIENTE -> usuarioRepository.existsByIdAndBloqueadoEmIsNotNull(usuario.id());
            case CHEFE -> chefeRepository.existsByIdAndBloqueadoEmIsNotNull(usuario.id());
            case MODERADOR -> false;
        };
    }
}
