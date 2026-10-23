package com.couto.chefe_api.service;

import com.couto.chefe_api.domain.Avaliacao;
import com.couto.chefe_api.domain.DenunciaAvaliacao;
import com.couto.chefe_api.dto.DenunciaAvaliacaoRequestDto;
import com.couto.chefe_api.dto.DenunciaCriadaDto;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.exception.ConflitoException;
import com.couto.chefe_api.repository.DenunciaAvaliacaoRepository;
import com.couto.chefe_api.security.ModeracaoRegistry;
import com.couto.chefe_api.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DenunciaAvaliacaoService {

    private final AvaliacaoService avaliacaoService;
    private final DenunciaAvaliacaoRepository denunciaRepository;
    private final NotificacaoService notificacaoService;
    private final ModeracaoRegistry moderacaoRegistry;
    private final Clock clock;

    /** Só o chef avaliado denuncia, e só comentário publicado (o que alguém pode ter lido). */
    @Transactional
    public DenunciaCriadaDto denunciar(Long avaliacaoId, UsuarioAutenticado chef, DenunciaAvaliacaoRequestDto dto) {
        Avaliacao avaliacao = avaliacaoService.sobreOChef(avaliacaoId, chef);
        AvaliacaoService.exigirComentarioPublicado(avaliacao, "Só é possível denunciar comentário publicado");
        if (denunciaRepository.existsByAvaliacao_IdAndDenuncianteId(avaliacao.getId(), chef.id())) {
            throw new ConflitoException("Você já denunciou esta avaliação");
        }

        DenunciaAvaliacao denuncia = new DenunciaAvaliacao();
        denuncia.setAvaliacao(avaliacao);
        denuncia.setDenuncianteId(chef.id());
        denuncia.setDenuncianteTipo(chef.tipo());
        denuncia.setAutorId(avaliacao.getAutorId());
        denuncia.setAutorTipo(avaliacao.getAutorTipo());
        denuncia.setTextoDenunciado(avaliacao.getComentario());
        denuncia.setMotivo(dto.motivo());
        denuncia.setCriadaEm(LocalDateTime.now(clock));
        denuncia.setDescricao(dto.descricao() == null || dto.descricao().isBlank() ? null : dto.descricao().strip());
        DenunciaAvaliacao salva = denunciaRepository.save(denuncia);

        String referencia = String.valueOf(salva.getId());
        moderacaoRegistry.ids().forEach(id -> notificacaoService.notificar(id, TipoUsuario.MODERADOR,
                EventoNotificacao.AVALIACAO_DENUNCIADA, "Nova denúncia de avaliação (" + dto.motivo() + ")", referencia));
        return new DenunciaCriadaDto(salva.getId(), salva.getStatus());
    }
}
