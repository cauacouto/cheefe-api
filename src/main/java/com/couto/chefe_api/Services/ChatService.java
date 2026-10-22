package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.ConversaDto;
import com.couto.chefe_api.Dtos.MensagemDto;
import com.couto.chefe_api.Excepitons.ChefeNaoEncontradoException;
import com.couto.chefe_api.Excepitons.RecursoNaoEncontradoException;
import com.couto.chefe_api.Excepitons.RegraDeNegocioException;
import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.Security.UsuarioAutenticado;
import com.couto.chefe_api.Services.eventos.MensagemEnviadaEvent;
import com.couto.chefe_api.Services.eventos.MensagensLidasEvent;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.Conversa;
import com.couto.chefe_api.domin.Mensagem;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.ConversaRepository;
import com.couto.chefe_api.repositorys.MensagemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Regras do chat: só participantes veem/mexem na conversa (os demais recebem 404); só o cliente inicia. */
@Service
@RequiredArgsConstructor
public class ChatService {

    static final int TAMANHO_MAXIMO_PAGINA = 100;
    static final int TAMANHO_PREVIA = 100;

    private final ConversaRepository conversaRepository;
    private final MensagemRepository mensagemRepository;
    private final UsuarioRepository usuarioRepository;
    private final ChefeRepository chefeRepository;
    private final NotificacaoService notificacaoService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public record ConversaAberta(ConversaDto conversa, boolean criada) {
    }

    @Transactional
    public ConversaAberta abrirConversa(UUID clienteId, UUID chefeId) {
        ChefeModel chefe = chefeRepository.findById(chefeId)
                .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                .orElseThrow(ChefeNaoEncontradoException::new);
        UserModel cliente = usuarioRepository.findById(clienteId).orElseThrow(UsuarioException::new);
        return conversaRepository.findByCliente_IdAndChefe_Id(clienteId, chefeId)
                .map(existente -> new ConversaAberta(paraDto(existente, clienteId, TipoUsuario.CLIENTE), false))
                .orElseGet(() -> {
                    Conversa nova = new Conversa();
                    nova.setCliente(cliente);
                    nova.setChefe(chefe);
                    return new ConversaAberta(paraDto(conversaRepository.save(nova), clienteId, TipoUsuario.CLIENTE), true);
                });
    }

    @Transactional(readOnly = true)
    public List<ConversaDto> listarConversas(UsuarioAutenticado usuario) {
        List<Conversa> conversas = switch (usuario.tipo()) {
            case CLIENTE -> conversaRepository.findByCliente_IdOrderByUltimaMensagemEmDesc(usuario.id());
            case CHEFE -> conversaRepository.findByChefe_IdOrderByUltimaMensagemEmDesc(usuario.id());
            case MODERADOR -> List.of();
        };
        return conversas.stream().map(c -> paraDto(c, usuario.id(), usuario.tipo())).toList();
    }

    @Transactional(readOnly = true)
    public List<MensagemDto> listarMensagens(Long conversaId, UsuarioAutenticado usuario, Long antesDe, int size) {
        Conversa conversa = doParticipante(conversaId, usuario);
        PageRequest pagina = PageRequest.of(0, Math.max(1, Math.min(size, TAMANHO_MAXIMO_PAGINA)));
        List<Mensagem> mensagens = antesDe == null
                ? mensagemRepository.findByConversa_IdOrderByIdDesc(conversa.getId(), pagina)
                : mensagemRepository.findByConversa_IdAndIdLessThanOrderByIdDesc(conversa.getId(), antesDe, pagina);
        return mensagens.stream().map(m -> MensagemDto.de(m, usuario.id())).toList();
    }

    @Transactional
    public MensagemDto enviar(Long conversaId, UsuarioAutenticado usuario, String texto) {
        Conversa conversa = doParticipante(conversaId, usuario);
        // chef apagado/banido: ninguém escreve mais nessa conversa (o histórico continua visível)
        conversa.getChefe().exigirAtivo();
        String normalizado = texto == null ? "" : texto.strip();
        if (normalizado.isEmpty()) {
            throw new RegraDeNegocioException("A mensagem não pode ficar em branco");
        }

        LocalDateTime agora = LocalDateTime.now(clock);
        Mensagem mensagem = new Mensagem();
        mensagem.setConversa(conversa);
        mensagem.setRemetenteId(usuario.id());
        mensagem.setRemetenteTipo(usuario.tipo());
        mensagem.setTexto(normalizado);
        mensagem.setEnviadaEm(agora);
        Mensagem salva = mensagemRepository.save(mensagem);
        conversa.setUltimaMensagemEm(agora);

        notificarDestinatario(conversa, usuario);
        eventPublisher.publishEvent(new MensagemEnviadaEvent(salva.getId()));
        return MensagemDto.de(salva, usuario.id());
    }

    @Transactional
    public void marcarLidas(Long conversaId, UsuarioAutenticado usuario) {
        Conversa conversa = doParticipante(conversaId, usuario);
        LocalDateTime agora = LocalDateTime.now(clock);
        int lidas = mensagemRepository.marcarLidas(conversa.getId(), usuario.id(), agora);
        notificacaoService.marcarLidasDaReferencia(usuario.id(), EventoNotificacao.MENSAGEM_RECEBIDA,
                String.valueOf(conversa.getId()));
        if (lidas > 0) {
            Long ateId = mensagemRepository.findFirstByConversa_IdOrderByIdDesc(conversa.getId()).map(Mensagem::getId).orElse(null);
            eventPublisher.publishEvent(new MensagensLidasEvent(conversa.getId(), usuario.id(), ateId, agora));
        }
    }

    /** Uma notificação por conversa enquanto não for lida: dez mensagens seguidas = um aviso. */
    private void notificarDestinatario(Conversa conversa, UsuarioAutenticado remetente) {
        boolean remetenteEhCliente = remetente.tipo() == TipoUsuario.CLIENTE;
        UUID destinatario = remetenteEhCliente ? conversa.getChefe().getId() : conversa.getCliente().getId();
        TipoUsuario tipoDestinatario = remetenteEhCliente ? TipoUsuario.CHEFE : TipoUsuario.CLIENTE;
        String nomeRemetente = remetenteEhCliente ? conversa.getCliente().getNome() : conversa.getChefe().getNome();
        String referencia = String.valueOf(conversa.getId());
        if (!notificacaoService.existeNaoLida(destinatario, EventoNotificacao.MENSAGEM_RECEBIDA, referencia)) {
            notificacaoService.notificar(destinatario, tipoDestinatario, EventoNotificacao.MENSAGEM_RECEBIDA,
                    "Nova mensagem de " + nomeRemetente, referencia);
        }
    }

    /** Conversa de que o usuário participa; para qualquer outro, ela "não existe". */
    Conversa doParticipante(Long conversaId, UsuarioAutenticado usuario) {
        return conversaRepository.findById(conversaId)
                .filter(c -> participa(c, usuario))
                .orElseThrow(() -> new RecursoNaoEncontradoException("conversa não encontrada"));
    }

    static boolean participa(Conversa conversa, UsuarioAutenticado usuario) {
        return switch (usuario.tipo()) {
            case CLIENTE -> conversa.getCliente().getId().equals(usuario.id());
            case CHEFE -> conversa.getChefe().getId().equals(usuario.id());
            case MODERADOR -> false;
        };
    }

    private ConversaDto paraDto(Conversa conversa, UUID leitorId, TipoUsuario tipoLeitor) {
        ConversaDto.ParticipanteDto outro = tipoLeitor == TipoUsuario.CLIENTE
                ? new ConversaDto.ParticipanteDto(conversa.getChefe().getId(), conversa.getChefe().getNome(),
                        conversa.getChefe().getImageUrl(), TipoUsuario.CHEFE)
                : new ConversaDto.ParticipanteDto(conversa.getCliente().getId(), conversa.getCliente().getNome(),
                        conversa.getCliente().getImageUrl(), TipoUsuario.CLIENTE);
        ConversaDto.UltimaMensagemDto ultima = mensagemRepository.findFirstByConversa_IdOrderByIdDesc(conversa.getId())
                .map(m -> new ConversaDto.UltimaMensagemDto(
                        m.isApagada() ? null : previa(m.getTexto()), m.getEnviadaEm(), m.getRemetenteId().equals(leitorId),
                        m.isApagada()))
                .orElse(null);
        long naoLidas = conversa.getId() == null ? 0
                : mensagemRepository.countByConversa_IdAndRemetenteIdNotAndLidaEmIsNull(conversa.getId(), leitorId);
        return new ConversaDto(conversa.getId(), outro, ultima, naoLidas, conversa.getUltimaMensagemEm());
    }

    private static String previa(String texto) {
        return texto.length() <= TAMANHO_PREVIA ? texto : texto.substring(0, TAMANHO_PREVIA);
    }
}
