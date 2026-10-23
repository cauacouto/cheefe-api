package com.couto.chefe_api.service;

import com.couto.chefe_api.enums.TipoUsuario;
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
     * Moderadores nunca são bloqueados: a exceção sai, mas nenhuma conta é alterada nem entra na fila.
     * <p>
     * Chamar ANTES de travar ou gravar a linha do autor: a transação interna (REQUIRES_NEW) atualiza a
     * conta do autor e ficaria esperando o lock da transação externa.
     */
    public String verificar(String texto, UsuarioAutenticado autor) {
        ResultadoFiltro resultado = filtro.analisar(texto);
        if (resultado.violacao().isPresent()) {
            if (autor.tipo() == TipoUsuario.MODERADOR) {
                throw new ConteudoBloqueadoException();
            }
            bloqueioContaService.bloquear(autor.id(), autor.tipo(), resultado.violacao().get(), texto);
            throw new ConteudoBloqueadoException();
        }
        return resultado.textoExibido();
    }
}
