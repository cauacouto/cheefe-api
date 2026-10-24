package com.couto.chefe_api.service;

import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.exception.TelefoneNaoVerificadoException;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Agendar, conversar e avaliar exigem telefone verificado (o resto não: ninguém fica preso com agendamento em
 * andamento). Uma leitura por chave primária, sem trava: pode rodar antes do filtro de conteúdo.
 */
@Component
@RequiredArgsConstructor
public class ExigenciaTelefone {

    private final UsuarioRepository usuarioRepository;
    private final ChefeRepository chefeRepository;

    public void exigir(UsuarioAutenticado conta) {
        exigir(conta.id(), conta.tipo());
    }

    public void exigir(UUID id, TipoUsuario tipo) {
        boolean verificado = switch (tipo) {
            case CLIENTE -> usuarioRepository.existsByIdAndTelefoneVerificadoIsNotNull(id);
            case CHEFE -> chefeRepository.existsByIdAndTelefoneVerificadoIsNotNull(id);
            case MODERADOR -> false;
        };
        if (!verificado) {
            throw new TelefoneNaoVerificadoException();
        }
    }
}
