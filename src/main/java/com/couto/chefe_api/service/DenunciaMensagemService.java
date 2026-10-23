package com.couto.chefe_api.service;

import com.couto.chefe_api.dto.DenunciaCriadaDto;
import com.couto.chefe_api.dto.DenunciaMensagemRequestDto;
import com.couto.chefe_api.exception.ConflitoException;
import com.couto.chefe_api.exception.RecursoNaoEncontradoException;
import com.couto.chefe_api.security.ModeracaoRegistry;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.domain.Conversa;
import com.couto.chefe_api.domain.DenunciaMensagem;
import com.couto.chefe_api.domain.Mensagem;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.ConversaRepository;
import com.couto.chefe_api.repository.DenunciaMensagemRepository;
import com.couto.chefe_api.repository.MensagemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DenunciaMensagemService {

    private final ConversaRepository conversaRepository;
    private final MensagemRepository mensagemRepository;
    private final DenunciaMensagemRepository denunciaRepository;
    private final NotificacaoService notificacaoService;
    private final ModeracaoRegistry moderacaoRegistry;
    private final Clock clock;

    /** Só quem recebeu a mensagem denuncia; para o autor e para quem não participa ela "não existe". */
    @Transactional
    public DenunciaCriadaDto denunciar(Long conversaId, Long mensagemId, UsuarioAutenticado usuario,
                                       DenunciaMensagemRequestDto dto) {
        Conversa conversa = conversaRepository.findById(conversaId)
                .filter(c -> ChatService.participa(c, usuario))
                .orElseThrow(() -> new RecursoNaoEncontradoException("mensagem não encontrada"));
        ChatService.exigirChefAtivo(conversa, usuario);
        Mensagem mensagem = mensagemRepository.findByIdAndConversa_Id(mensagemId, conversa.getId())
                .filter(m -> !m.getRemetenteId().equals(usuario.id()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("mensagem não encontrada"));
        if (mensagem.isApagada()) {
            throw new ConflitoException("mensagem apagada não pode ser denunciada");
        }
        if (denunciaRepository.existsByMensagem_IdAndDenuncianteId(mensagem.getId(), usuario.id())) {
            throw new ConflitoException("você já denunciou esta mensagem");
        }

        DenunciaMensagem denuncia = new DenunciaMensagem();
        denuncia.setMensagem(mensagem);
        denuncia.setDenuncianteId(usuario.id());
        denuncia.setDenuncianteTipo(usuario.tipo());
        denuncia.setAutorId(mensagem.getRemetenteId());
        denuncia.setAutorTipo(mensagem.getRemetenteTipo());
        denuncia.setTextoDenunciado(mensagem.getTexto());
        denuncia.setTextoOriginal(mensagem.getTextoOriginal());
        denuncia.setMotivo(dto.motivo());
        denuncia.setCriadaEm(LocalDateTime.now(clock));
        denuncia.setDescricao(dto.descricao() == null || dto.descricao().isBlank() ? null : dto.descricao().strip());
        DenunciaMensagem salva = denunciaRepository.save(denuncia);

        String referencia = String.valueOf(salva.getId());
        moderacaoRegistry.ids().forEach(id -> notificacaoService.notificar(id, TipoUsuario.MODERADOR,
                EventoNotificacao.MENSAGEM_DENUNCIADA, "Nova denúncia de mensagem (" + dto.motivo() + ")", referencia));
        return new DenunciaCriadaDto(salva.getId(), salva.getStatus());
    }
}
