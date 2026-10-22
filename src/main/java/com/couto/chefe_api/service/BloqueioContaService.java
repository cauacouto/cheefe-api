package com.couto.chefe_api.service;

import com.couto.chefe_api.domain.BloqueioConta;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.BloqueioContaRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.security.ModeracaoRegistry;
import com.couto.chefe_api.service.FiltroConteudo.Violacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BloqueioContaService {

    private final UsuarioRepository usuarioRepository;
    private final ChefeRepository chefeRepository;
    private final BloqueioContaRepository bloqueioRepository;
    private final NotificacaoService notificacaoService;
    private final ModeracaoRegistry moderacaoRegistry;
    private final Clock clock;

    /**
     * Transação própria: o bloqueio permanece mesmo que a requisição que o provocou seja desfeita
     * (a avaliação/mensagem com ódio nunca é gravada, mas a conta continua suspensa).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BloqueioConta bloquear(UUID contaId, TipoUsuario contaTipo, Violacao violacao, String texto) {
        LocalDateTime agora = LocalDateTime.now(clock);
        String motivo = violacao.categoria().name();
        if (contaTipo == TipoUsuario.CHEFE) {
            chefeRepository.findById(contaId).ifPresent(c -> {
                c.setBloqueadoEm(agora);
                c.setBloqueioMotivo(cortar(motivo, 255));
            });
        } else {
            usuarioRepository.findById(contaId).ifPresent(u -> {
                u.setBloqueadoEm(agora);
                u.setBloqueioMotivo(cortar(motivo, 255));
            });
        }

        BloqueioConta bloqueio = new BloqueioConta();
        bloqueio.setContaId(contaId);
        bloqueio.setContaTipo(contaTipo);
        bloqueio.setCategoria(violacao.categoria());
        bloqueio.setTermo(cortar(violacao.termo(), 255));
        bloqueio.setTexto(cortar(texto, 2000));
        bloqueio.setCriadoEm(agora);
        bloqueio = bloqueioRepository.save(bloqueio);

        String mensagem = "Conta bloqueada automaticamente (" + motivo + ") — revisar";
        for (UUID moderador : moderacaoRegistry.ids()) {
            notificacaoService.notificar(moderador, TipoUsuario.MODERADOR,
                    EventoNotificacao.CONTA_BLOQUEADA, mensagem, String.valueOf(bloqueio.getId()));
        }
        return bloqueio;
    }

    private static String cortar(String valor, int max) {
        if (valor == null) {
            return "";
        }
        return valor.length() <= max ? valor : valor.substring(0, max);
    }
}
