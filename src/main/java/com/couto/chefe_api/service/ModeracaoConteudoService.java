package com.couto.chefe_api.service;

import com.couto.chefe_api.exception.ConteudoBloqueadoException;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.service.FiltroConteudo.ResultadoFiltro;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModeracaoConteudoService {

    private final FiltroConteudo filtro;
    private final BloqueioContaService bloqueioContaService;

    /**
     * Devolve o texto exibível (ofensas viram "***"). Ódio ou ameaça bloqueiam a conta do autor e
     * lançam {@link ConteudoBloqueadoException}; o bloqueio é commitado em transação própria.
     */
    public String verificar(String texto, UsuarioAutenticado autor) {
        ResultadoFiltro resultado = filtro.analisar(texto);
        if (resultado.violacao().isPresent()) {
            bloqueioContaService.bloquear(autor.id(), autor.tipo(), resultado.violacao().get(), texto);
            throw new ConteudoBloqueadoException();
        }
        return resultado.textoExibido();
    }
}
