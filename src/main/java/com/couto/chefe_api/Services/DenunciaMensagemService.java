package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.DenunciaCriadaDto;
import com.couto.chefe_api.Dtos.DenunciaMensagemRequestDto;
import com.couto.chefe_api.Excepitons.ConflitoException;
import com.couto.chefe_api.Excepitons.RecursoNaoEncontradoException;
import com.couto.chefe_api.Security.ModeracaoRegistry;
import com.couto.chefe_api.Security.UsuarioAutenticado;
import com.couto.chefe_api.domin.Conversa;
import com.couto.chefe_api.domin.DenunciaMensagem;
import com.couto.chefe_api.domin.Mensagem;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.ConversaRepository;
import com.couto.chefe_api.repositorys.DenunciaMensagemRepository;
import com.couto.chefe_api.repositorys.MensagemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DenunciaMensagemService {

    private final ConversaRepository conversaRepository;
    private final MensagemRepository mensagemRepository;
    private final DenunciaMensagemRepository denunciaRepository;
    private final NotificacaoService notificacaoService;
    private final ModeracaoRegistry moderacaoRegistry;

    /** Só quem recebeu a mensagem denuncia; para o autor e para quem não participa ela "não existe". */
    @Transactional
    public DenunciaCriadaDto denunciar(Long conversaId, Long mensagemId, UsuarioAutenticado usuario,
                                       DenunciaMensagemRequestDto dto) {
        Mensagem mensagem = conversaRepository.findById(conversaId)
                .filter(c -> ChatService.participa(c, usuario))
                .map(Conversa::getId)
                .flatMap(id -> mensagemRepository.findByIdAndConversa_Id(mensagemId, id))
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
        denuncia.setMotivo(dto.motivo());
        denuncia.setDescricao(dto.descricao() == null || dto.descricao().isBlank() ? null : dto.descricao().strip());
        DenunciaMensagem salva = denunciaRepository.save(denuncia);

        String referencia = String.valueOf(salva.getId());
        moderacaoRegistry.ids().forEach(id -> notificacaoService.notificar(id, TipoUsuario.MODERADOR,
                EventoNotificacao.MENSAGEM_DENUNCIADA, "Nova denúncia de mensagem (" + dto.motivo() + ")", referencia));
        return new DenunciaCriadaDto(salva.getId(), salva.getStatus());
    }
}
