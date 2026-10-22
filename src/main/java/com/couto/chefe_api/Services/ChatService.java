package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.ConversaDto;
import com.couto.chefe_api.Dtos.MensagemDto;
import com.couto.chefe_api.Excepitons.ChefeNaoEncontradoException;
import com.couto.chefe_api.Excepitons.ConflitoException;
import com.couto.chefe_api.Excepitons.RecursoNaoEncontradoException;
import com.couto.chefe_api.Excepitons.RegraDeNegocioException;
import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.Security.UsuarioAutenticado;
import com.couto.chefe_api.Services.eventos.MensagemApagadaEvent;
import com.couto.chefe_api.Services.eventos.MensagemEditadaEvent;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Regras do chat: só participantes veem/mexem na conversa (os demais recebem 404); só o cliente inicia. */
@Service
@RequiredArgsConstructor
public class ChatService {

    static final int TAMANHO_MAXIMO_PAGINA = 100;
    static final int TAMANHO_MAXIMO_PAGINA_CONVERSAS = 50;
    static final int TAMANHO_PREVIA = 100;
    static final Duration PRAZO_EDICAO = Duration.ofMinutes(15);

    private final ConversaRepository conversaRepository;
    private final MensagemRepository mensagemRepository;
    private final UsuarioRepository usuarioRepository;
    private final ChefeRepository chefeRepository;
    private final NotificacaoService notificacaoService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final TransactionTemplate transacao;

    public record ConversaAberta(ConversaDto conversa, boolean criada) {
    }

    /**
     * Idempotente também em paralelo (duplo clique, duas abas): se outro pedido criou a conversa no meio,
     * a constraint única barra a segunda e uma nova transação devolve a que ficou.
     */
    public ConversaAberta abrirConversa(UUID clienteId, UUID chefeId) {
        try {
            return transacao.execute(status -> abrirOuCriar(clienteId, chefeId));
        } catch (DataIntegrityViolationException corrida) {
            return transacao.execute(status -> abrirOuCriar(clienteId, chefeId));
        }
    }

    private ConversaAberta abrirOuCriar(UUID clienteId, UUID chefeId) {
        ChefeModel chefe = chefeRepository.findById(chefeId)
                .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                .orElseThrow(ChefeNaoEncontradoException::new);
        UserModel cliente = usuarioRepository.findById(clienteId).orElseThrow(UsuarioException::new);
        return conversaRepository.findByCliente_IdAndChefe_Id(clienteId, chefeId)
                .map(existente -> new ConversaAberta(paraDto(existente, clienteId, TipoUsuario.CLIENTE), false))
                .orElseGet(() -> {
                    LocalDateTime agora = LocalDateTime.now(clock);
                    Conversa nova = new Conversa();
                    nova.setCliente(cliente);
                    nova.setChefe(chefe);
                    nova.setCriadaEm(agora);
                    nova.setUltimaMensagemEm(agora);
                    // flush aqui: a violação da constraint sai dentro da transação, onde abrirConversa a trata
                    return new ConversaAberta(paraDto(conversaRepository.saveAndFlush(nova), clienteId, TipoUsuario.CLIENTE), true);
                });
    }

    /** Página de conversas, a mais recente primeiro; a ordenação vinda do cliente é ignorada. */
    @Transactional(readOnly = true)
    public Page<ConversaDto> listarConversas(UsuarioAutenticado usuario, Pageable pageable) {
        PageRequest pagina = PageRequest.of(pageable.getPageNumber(),
                Math.max(1, Math.min(pageable.getPageSize(), TAMANHO_MAXIMO_PAGINA_CONVERSAS)));
        Page<Conversa> conversas = switch (usuario.tipo()) {
            case CLIENTE -> conversaRepository.findByCliente_IdOrderByUltimaMensagemEmDescIdDesc(usuario.id(), pagina);
            case CHEFE -> conversaRepository.findByChefe_IdOrderByUltimaMensagemEmDescIdDesc(usuario.id(), pagina);
            case MODERADOR -> Page.empty(pagina);
        };
        return new PageImpl<>(paraDtos(conversas.getContent(), usuario.id(), usuario.tipo()), pagina,
                conversas.getTotalElements());
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
        Conversa conversa = conversaRepository.travarPorId(conversaId)
                .filter(c -> participa(c, usuario))
                .orElseThrow(() -> new RecursoNaoEncontradoException("conversa não encontrada"));
        // chef apagado/banido: ninguém escreve mais nessa conversa (o histórico continua visível)
        conversa.getChefe().exigirAtivo();
        String normalizado = normalizar(texto);

        LocalDateTime agora = LocalDateTime.now(clock);
        Mensagem mensagem = new Mensagem();
        mensagem.setConversa(conversa);
        mensagem.setRemetenteId(usuario.id());
        mensagem.setRemetenteTipo(usuario.tipo());
        mensagem.setTexto(normalizado);
        mensagem.setEnviadaEm(agora);
        Mensagem salva = mensagemRepository.save(mensagem);
        conversa.setUltimaMensagemEm(agora);

        UUID destinatario = outroParticipante(conversa, usuario);
        notificarDestinatario(conversa, usuario, destinatario);
        MensagemDto dto = MensagemDto.de(salva, usuario.id());
        eventPublisher.publishEvent(new MensagemEnviadaEvent(usuario.id(), dto, destinatario, MensagemDto.de(salva, destinatario)));
        return dto;
    }

    /** Só o autor, até 15 minutos depois do envio. */
    @Transactional
    public MensagemDto editar(Long conversaId, Long mensagemId, UsuarioAutenticado usuario, String texto) {
        Mensagem mensagem = doAutor(conversaId, mensagemId, usuario);
        if (mensagem.isApagada()) {
            throw new ConflitoException("Mensagem apagada não pode ser editada");
        }
        LocalDateTime agora = LocalDateTime.now(clock);
        if (agora.isAfter(mensagem.getEnviadaEm().plus(PRAZO_EDICAO))) {
            throw new ConflitoException("O prazo para editar a mensagem (15 minutos) já passou");
        }
        mensagem.setTexto(normalizar(texto));
        mensagem.setEditadaEm(agora);
        MensagemDto dto = MensagemDto.de(mensagem, usuario.id());
        UUID outro = outroParticipante(mensagem.getConversa(), usuario);
        eventPublisher.publishEvent(new MensagemEditadaEvent(usuario.id(), dto, outro, MensagemDto.de(mensagem, outro)));
        return dto;
    }

    /** Só o autor, a qualquer momento; o texto fica guardado (prova para denúncias). Apagar de novo não faz nada. */
    @Transactional
    public void apagar(Long conversaId, Long mensagemId, UsuarioAutenticado usuario) {
        Mensagem mensagem = doAutor(conversaId, mensagemId, usuario);
        if (mensagem.isApagada()) {
            return;
        }
        mensagem.setApagadaEm(LocalDateTime.now(clock));
        Conversa conversa = mensagem.getConversa();
        eventPublisher.publishEvent(new MensagemApagadaEvent(conversa.getId(), mensagem.getId(),
                conversa.getCliente().getId(), conversa.getChefe().getId()));
    }

    @Transactional
    public void marcarLidas(Long conversaId, UsuarioAutenticado usuario) {
        Conversa conversa = doParticipante(conversaId, usuario);
        exigirChefAtivo(conversa, usuario);
        LocalDateTime agora = LocalDateTime.now(clock);
        notificacaoService.marcarLidasDaReferencia(usuario.id(), EventoNotificacao.MENSAGEM_RECEBIDA,
                String.valueOf(conversa.getId()));
        // fixa o limite antes do UPDATE: mensagem que chegar no meio não ganha "visto" sem ter sido lida
        Long ateId = mensagemRepository.findFirstByConversa_IdOrderByIdDesc(conversa.getId()).map(Mensagem::getId).orElse(null);
        if (ateId == null) {
            return;
        }
        int lidas = mensagemRepository.marcarLidas(conversa.getId(), usuario.id(), ateId, agora);
        if (lidas > 0) {
            eventPublisher.publishEvent(new MensagensLidasEvent(conversa.getId(), usuario.id(),
                    outroParticipante(conversa, usuario), ateId, agora));
        }
    }

    /** Uma notificação por conversa enquanto não for lida: dez mensagens seguidas = um aviso. */
    private void notificarDestinatario(Conversa conversa, UsuarioAutenticado remetente, UUID destinatario) {
        boolean remetenteEhCliente = remetente.tipo() == TipoUsuario.CLIENTE;
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

    /** Chef apagado/banido com token ainda válido não age mais no chat; só lê o histórico. */
    static void exigirChefAtivo(Conversa conversa, UsuarioAutenticado usuario) {
        if (usuario.tipo() == TipoUsuario.CHEFE) {
            conversa.getChefe().exigirAtivo();
        }
    }

    private static UUID outroParticipante(Conversa conversa, UsuarioAutenticado usuario) {
        return usuario.tipo() == TipoUsuario.CLIENTE ? conversa.getChefe().getId() : conversa.getCliente().getId();
    }

    /** Mensagem da conversa escrita pelo usuário; mensagem dos outros, para ele, "não existe". */
    private Mensagem doAutor(Long conversaId, Long mensagemId, UsuarioAutenticado usuario) {
        Conversa conversa = doParticipante(conversaId, usuario);
        exigirChefAtivo(conversa, usuario);
        return mensagemRepository.findByIdAndConversa_Id(mensagemId, conversa.getId())
                .filter(m -> m.getRemetenteId().equals(usuario.id()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("mensagem não encontrada"));
    }

    private static String normalizar(String texto) {
        String normalizado = texto == null ? "" : texto.strip();
        if (normalizado.isEmpty()) {
            throw new RegraDeNegocioException("A mensagem não pode ficar em branco");
        }
        return normalizado;
    }

    static boolean participa(Conversa conversa, UsuarioAutenticado usuario) {
        return switch (usuario.tipo()) {
            case CLIENTE -> conversa.getCliente().getId().equals(usuario.id());
            case CHEFE -> conversa.getChefe().getId().equals(usuario.id());
            case MODERADOR -> false;
        };
    }

    private ConversaDto paraDto(Conversa conversa, UUID leitorId, TipoUsuario tipoLeitor) {
        return paraDtos(List.of(conversa), leitorId, tipoLeitor).get(0);
    }

    /** Última mensagem e não lidas de todas as conversas em duas queries, não duas por conversa. */
    private List<ConversaDto> paraDtos(List<Conversa> conversas, UUID leitorId, TipoUsuario tipoLeitor) {
        if (conversas.isEmpty()) {
            return List.of();
        }
        List<Long> ids = conversas.stream().map(Conversa::getId).toList();
        Map<Long, Mensagem> ultimas = mensagemRepository.ultimasDasConversas(ids).stream()
                .collect(Collectors.toMap(m -> m.getConversa().getId(), m -> m));
        Map<Long, Long> naoLidas = mensagemRepository.naoLidasPorConversa(ids, leitorId).stream()
                .collect(Collectors.toMap(MensagemRepository.NaoLidas::getConversaId, MensagemRepository.NaoLidas::getTotal));
        return conversas.stream().map(conversa -> {
            ConversaDto.ParticipanteDto outro = tipoLeitor == TipoUsuario.CLIENTE
                    ? new ConversaDto.ParticipanteDto(conversa.getChefe().getId(), conversa.getChefe().getNome(),
                            conversa.getChefe().getImageUrl(), TipoUsuario.CHEFE)
                    : new ConversaDto.ParticipanteDto(conversa.getCliente().getId(), conversa.getCliente().getNome(),
                            conversa.getCliente().getImageUrl(), TipoUsuario.CLIENTE);
            Mensagem m = ultimas.get(conversa.getId());
            ConversaDto.UltimaMensagemDto ultima = m == null ? null : new ConversaDto.UltimaMensagemDto(
                    m.isApagada() ? null : previa(m.getTexto()), m.getEnviadaEm(), m.getRemetenteId().equals(leitorId),
                    m.isApagada());
            return new ConversaDto(conversa.getId(), outro, ultima, naoLidas.getOrDefault(conversa.getId(), 0L),
                    conversa.getUltimaMensagemEm());
        }).toList();
    }

    private static String previa(String texto) {
        // conta em code points: cortar por char pode partir um emoji ao meio
        return texto.codePointCount(0, texto.length()) <= TAMANHO_PREVIA ? texto
                : texto.substring(0, texto.offsetByCodePoints(0, TAMANHO_PREVIA));
    }
}
