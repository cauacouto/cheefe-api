package com.couto.chefe_api.service;

import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.Avaliacao;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.domain.Reputacao;
import com.couto.chefe_api.dto.AvaliacaoPendenteDto;
import com.couto.chefe_api.dto.AvaliacaoPublicaDto;
import com.couto.chefe_api.dto.ReputacaoDto;
import com.couto.chefe_api.dto.AvaliarRequestDto;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TagAvaliacaoChef;
import com.couto.chefe_api.enums.TagAvaliacaoCliente;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.exception.AgendamentoException;
import com.couto.chefe_api.exception.AvaliacaoForaDoPrazoException;
import com.couto.chefe_api.exception.ChefeNaoEncontradoException;
import com.couto.chefe_api.exception.RecursoNaoEncontradoException;
import com.couto.chefe_api.exception.ConflitoException;
import com.couto.chefe_api.exception.RegraDeNegocioException;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.AvaliacaoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Avaliações depois de um agendamento concluído: o cliente avalia cada chef e cada chef avalia o
 * cliente, até 7 dias depois da conclusão. A média é anônima (só contadores no avaliado).
 */
@Service
@RequiredArgsConstructor
public class AvaliacaoService {

    static final Duration PRAZO = Duration.ofDays(7);
    static final String MENSAGEM_RECEBIDA = "Você recebeu uma nova avaliação";
    static final String AUTOR_ANONIMO = "Cliente verificado";
    static final String COMENTARIO_REMOVIDO = "Comentário removido pela moderação";
    static final int TAMANHO_MAXIMO_PAGINA = 50;
    /** "outubro de 2026": só mês e ano, para a data não apontar o agendamento. */
    static final DateTimeFormatter MES_E_ANO = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", new Locale("pt", "BR"));

    private final AgendamentoRepository agendamentoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final ChefeRepository chefeRepository;
    private final UsuarioRepository usuarioRepository;
    private final ModeracaoConteudoService moderacaoConteudo;
    private final NotificacaoService notificacaoService;
    private final Clock clock;

    /** Quem está sendo avaliado nesta avaliação. */
    private record Avaliado(UUID id, String nome, TipoUsuario tipo) {
    }

    @Transactional
    public Long avaliar(Long agendamentoId, UsuarioAutenticado autor, AvaliarRequestDto dto) {
        LocalDateTime agora = LocalDateTime.now(clock);
        Agendamento agendamento = doParticipante(agendamentoId, autor);
        exigirChefAtivo(autor);
        exigirDentroDoPrazo(agendamento, agora);
        Avaliado avaliado = avaliadoPor(agendamento, autor, dto.chefeId());
        if (avaliacaoRepository.existsByAgendamento_IdAndAutorIdAndAvaliadoId(agendamento.getId(), autor.id(), avaliado.id())) {
            throw new ConflitoException("Você já avaliou essa pessoa neste agendamento");
        }
        Set<String> tags = validarTags(dto.tags(), avaliado.tipo());

        // antes de qualquer trava: o bloqueio do autor roda numa transação própria (ver ModeracaoConteudoService)
        String original = dto.comentario() == null || dto.comentario().isBlank() ? null : dto.comentario().strip();
        String exibido = original == null ? null : moderacaoConteudo.verificar(original, autor);

        somarNoAvaliado(avaliado, dto.nota());

        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setAgendamento(agendamento);
        avaliacao.setAutorId(autor.id());
        avaliacao.setAutorTipo(autor.tipo());
        avaliacao.setAvaliadoId(avaliado.id());
        avaliacao.setAvaliadoTipo(avaliado.tipo());
        avaliacao.setNota(dto.nota());
        avaliacao.setTags(tags);
        avaliacao.setComentario(exibido);
        avaliacao.setComentarioOriginal(original);
        // sem comentário não há nome a mostrar: entra sempre anônima e publicada
        boolean anonimo = original == null || !Boolean.FALSE.equals(dto.anonimo());
        avaliacao.setAnonimo(anonimo);
        avaliacao.setPublicadoEm(anonimo ? agora : null);
        Avaliacao salva = avaliacaoRepository.save(avaliacao);

        if (autor.tipo() == TipoUsuario.CHEFE) {
            // o chef avaliou: o comentário com nome que o cliente deixou sobre ele já pode aparecer
            avaliacaoRepository.findByAgendamento_IdAndAutorIdAndAvaliadoIdAndPublicadoEmIsNull(
                            agendamento.getId(), avaliado.id(), autor.id())
                    .ifPresent(doCliente -> doCliente.setPublicadoEm(agora));
        }

        // sem referência ao agendamento: apontá-lo revelaria quem avaliou
        notificacaoService.notificar(avaliado.id(), avaliado.tipo(), EventoNotificacao.AVALIACAO_RECEBIDA,
                MENSAGEM_RECEBIDA, null);
        return salva.getId();
    }

    /** Chamado pela {@link AvaliacaoRotina}, uma transação por avaliação. */
    @Transactional
    public void publicarComNome(Long avaliacaoId, LocalDateTime agora) {
        avaliacaoRepository.publicarSeEsperando(avaliacaoId, agora);
    }

    /** Quem eu ainda posso avaliar neste agendamento; vazio fora do status ou do prazo. */
    @Transactional(readOnly = true)
    public List<AvaliacaoPendenteDto> pendentes(Long agendamentoId, UsuarioAutenticado usuario) {
        LocalDateTime agora = LocalDateTime.now(clock);
        Agendamento agendamento = doParticipante(agendamentoId, usuario);
        if (!dentroDoPrazo(agendamento, agora)) {
            return List.of();
        }
        LocalDateTime prazo = agendamento.getConcluidoEm().plus(PRAZO);
        List<Avaliado> possiveis = new ArrayList<>();
        if (usuario.tipo() == TipoUsuario.CLIENTE) {
            agendamento.getChefes().forEach(c -> possiveis.add(new Avaliado(c.getId(), c.getNome(), TipoUsuario.CHEFE)));
        } else {
            UserModel cliente = agendamento.getUsuario();
            possiveis.add(new Avaliado(cliente.getId(), cliente.getNome(), TipoUsuario.CLIENTE));
        }
        return possiveis.stream()
                .filter(a -> !avaliacaoRepository.existsByAgendamento_IdAndAutorIdAndAvaliadoId(agendamento.getId(), usuario.id(), a.id()))
                .map(a -> new AvaliacaoPendenteDto(a.id(), a.nome(), a.tipo(), prazo))
                .toList();
    }

    /** Avaliações publicadas sobre o chef, das mais recentes; sem id de autor nem de agendamento. */
    @Transactional(readOnly = true)
    public Page<AvaliacaoPublicaDto> listaPublica(UUID chefeId, Pageable pageable) {
        exigirChef(chefeId);
        // a ordem é sempre a da publicação: a do cliente é ignorada
        Page<Avaliacao> pagina = avaliacaoRepository.findByAvaliadoIdAndAvaliadoTipoAndPublicadoEmIsNotNullOrderByPublicadoEmDescIdDesc(
                chefeId, TipoUsuario.CHEFE, PageRequest.of(pageable.getPageNumber(), tamanho(pageable)));
        Set<UUID> comNome = pagina.stream().filter(AvaliacaoService::mostraNome).map(Avaliacao::getAutorId)
                .collect(Collectors.toSet());
        Map<UUID, String> nomes = usuarioRepository.findAllById(comNome).stream()
                .collect(Collectors.toMap(UserModel::getId, UserModel::getNome));
        return pagina.map(a -> new AvaliacaoPublicaDto(a.getId(), a.getNota(), tagsDe(a), comentarioPublico(a),
                mostraNome(a) ? primeiroNome(nomes.get(a.getAutorId())) : AUTOR_ANONIMO,
                MES_E_ANO.format(a.getPublicadoEm()), a.getResposta()));
    }

    @Transactional(readOnly = true)
    public ReputacaoDto reputacaoDoChef(UUID chefeId) {
        ChefeModel chefe = exigirChef(chefeId);
        return reputacao(chefe.getId(), TipoUsuario.CHEFE, chefe.getSomaNotas(), chefe.getTotalAvaliacoes());
    }

    /** Só para chef que tem (ou teve) agendamento com o cliente; para os demais o cliente "não existe". */
    @Transactional(readOnly = true)
    public ReputacaoDto reputacaoDoCliente(UUID clienteId, UsuarioAutenticado chef) {
        if (!agendamentoRepository.existsByUsuario_IdAndChefes_Id(clienteId, chef.id())) {
            throw new RecursoNaoEncontradoException("cliente não encontrado");
        }
        UserModel cliente = usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("cliente não encontrado"));
        return reputacao(cliente.getId(), TipoUsuario.CLIENTE, cliente.getSomaNotas(), cliente.getTotalAvaliacoes());
    }

    /**
     * Resposta pública do chef avaliado; responder de novo substitui (edição). O cliente autor é
     * notificado só na primeira.
     */
    @Transactional
    public String responder(Long avaliacaoId, UsuarioAutenticado chef, String texto) {
        Avaliacao avaliacao = sobreOChef(avaliacaoId, chef);
        exigirComentarioPublicado(avaliacao, "Só é possível responder comentário publicado");
        String original = texto.strip();
        String exibido = moderacaoConteudo.verificar(original, chef);
        boolean primeira = avaliacao.getRespondidaEm() == null;
        avaliacao.setResposta(exibido);
        avaliacao.setRespostaOriginal(original);
        avaliacao.setRespondidaEm(LocalDateTime.now(clock));
        if (primeira) {
            String nome = chefeRepository.findById(chef.id()).map(ChefeModel::getNome).orElse("");
            notificacaoService.notificar(avaliacao.getAutorId(), avaliacao.getAutorTipo(),
                    EventoNotificacao.AVALIACAO_RESPONDIDA, "Chef " + nome + " respondeu sua avaliação",
                    String.valueOf(avaliacao.getId()));
        }
        return exibido;
    }

    /** Avaliação que um cliente fez sobre este chef; para qualquer outro ela "não existe". */
    Avaliacao sobreOChef(Long avaliacaoId, UsuarioAutenticado chef) {
        Avaliacao avaliacao = avaliacaoRepository.findById(avaliacaoId)
                .filter(a -> a.getAvaliadoTipo() == TipoUsuario.CHEFE && a.getAvaliadoId().equals(chef.id()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("avaliação não encontrada"));
        exigirChefAtivo(chef);
        return avaliacao;
    }

    /** Chef apagado ou banido (ativo=false) não opera, mesmo com o token ainda válido. */
    private void exigirChefAtivo(UsuarioAutenticado usuario) {
        if (usuario.tipo() == TipoUsuario.CHEFE) {
            chefeRepository.findById(usuario.id()).orElseThrow(ChefeNaoEncontradoException::new).exigirAtivo();
        }
    }

    /** Resposta e denúncia só valem para comentário que está na lista pública (e não foi removido). */
    static void exigirComentarioPublicado(Avaliacao avaliacao, String mensagem) {
        if (avaliacao.getComentario() == null || avaliacao.getPublicadoEm() == null || avaliacao.isComentarioRemovido()) {
            throw new ConflitoException(mensagem);
        }
    }

    /** Chef bloqueado ou banido/inativo não aparece em lugar público: a reputação dele também não. */
    private ChefeModel exigirChef(UUID chefeId) {
        return chefeRepository.findById(chefeId)
                .filter(c -> Boolean.TRUE.equals(c.getAtivo()) && c.getBloqueadoEm() == null)
                .orElseThrow(ChefeNaoEncontradoException::new);
    }

    /** O primeiro nome só aparece se o cliente escolheu e o comentário continua lá. */
    private static boolean mostraNome(Avaliacao avaliacao) {
        return !avaliacao.isAnonimo() && !avaliacao.isComentarioRemovido();
    }

    private ReputacaoDto reputacao(UUID id, TipoUsuario tipo, int soma, int total) {
        List<ReputacaoDto.TagContagem> tags = avaliacaoRepository.contagemDeTags(id, tipo).stream()
                .map(linha -> rotulo(tipo, (String) linha[0])
                        .map(t -> new ReputacaoDto.TagContagem(t.codigo(), t.rotulo(), (Long) linha[1])))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingLong(ReputacaoDto.TagContagem::total).reversed()
                        .thenComparing(t -> ordem(tipo, t.codigo())))
                .toList();
        return new ReputacaoDto(Reputacao.media(soma, total), total, tags);
    }

    private static List<AvaliacaoPublicaDto.Tag> tagsDe(Avaliacao avaliacao) {
        return avaliacao.getTags().stream()
                .sorted(Comparator.comparingInt(t -> ordem(avaliacao.getAvaliadoTipo(), t)))
                .map(t -> rotulo(avaliacao.getAvaliadoTipo(), t))
                .flatMap(Optional::stream)
                .toList();
    }

    private static String comentarioPublico(Avaliacao avaliacao) {
        return avaliacao.isComentarioRemovido() ? COMENTARIO_REMOVIDO : avaliacao.getComentario();
    }

    private static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return AUTOR_ANONIMO;
        }
        return nome.trim().split("\\s+")[0];
    }

    /** Tag da lista do lado avaliado; uma tag que saiu da lista some da resposta. */
    private static Optional<AvaliacaoPublicaDto.Tag> rotulo(TipoUsuario avaliadoTipo, String codigo) {
        try {
            String rotulo = avaliadoTipo == TipoUsuario.CHEFE
                    ? TagAvaliacaoChef.valueOf(codigo).rotulo()
                    : TagAvaliacaoCliente.valueOf(codigo).rotulo();
            return Optional.of(new AvaliacaoPublicaDto.Tag(codigo, rotulo));
        } catch (IllegalArgumentException desconhecida) {
            return Optional.empty();
        }
    }

    /** Posição na lista do spec, para a ordem não depender do banco. */
    private static int ordem(TipoUsuario avaliadoTipo, String codigo) {
        try {
            return avaliadoTipo == TipoUsuario.CHEFE
                    ? TagAvaliacaoChef.valueOf(codigo).ordinal()
                    : TagAvaliacaoCliente.valueOf(codigo).ordinal();
        } catch (IllegalArgumentException desconhecida) {
            return Integer.MAX_VALUE;
        }
    }

    private static int tamanho(Pageable pageable) {
        return Math.max(1, Math.min(pageable.getPageSize(), TAMANHO_MAXIMO_PAGINA));
    }

    /** Cliente dono ou chef que está no agendamento; para os demais ele "não existe". */
    private Agendamento doParticipante(Long agendamentoId, UsuarioAutenticado usuario) {
        return agendamentoRepository.findById(agendamentoId)
                .filter(a -> switch (usuario.tipo()) {
                    case CLIENTE -> a.getUsuario() != null && a.getUsuario().getId().equals(usuario.id());
                    case CHEFE -> a.getChefes().stream().anyMatch(c -> c.getId().equals(usuario.id()));
                    case MODERADOR -> false;
                })
                .orElseThrow(AgendamentoException::new);
    }

    private void exigirDentroDoPrazo(Agendamento agendamento, LocalDateTime agora) {
        if (agendamento.getStatus() != StatusAgendamento.CONCLUIDO) {
            throw AvaliacaoForaDoPrazoException.naoConcluido();
        }
        if (!dentroDoPrazo(agendamento, agora)) {
            throw AvaliacaoForaDoPrazoException.prazoVencido();
        }
    }

    /** Sem data de conclusão (concluído antes desta versão) não há prazo: não é avaliável. */
    private static boolean dentroDoPrazo(Agendamento agendamento, LocalDateTime agora) {
        return agendamento.getStatus() == StatusAgendamento.CONCLUIDO
                && agendamento.getConcluidoEm() != null
                && !agora.isAfter(agendamento.getConcluidoEm().plus(PRAZO));
    }

    private static Avaliado avaliadoPor(Agendamento agendamento, UsuarioAutenticado autor, UUID chefeId) {
        if (autor.tipo() == TipoUsuario.CHEFE) {
            UserModel cliente = agendamento.getUsuario();
            return new Avaliado(cliente.getId(), cliente.getNome(), TipoUsuario.CLIENTE);
        }
        if (chefeId == null) {
            throw new RegraDeNegocioException("Informe qual chef você está avaliando (chefeId)");
        }
        // chef que recusou ou desistiu saiu do agendamento: não aparece para avaliar
        ChefeModel chefe = agendamento.getChefes().stream()
                .filter(c -> c.getId().equals(chefeId))
                .findFirst()
                .orElseThrow(() -> new AgendamentoException("chef não encontrado neste agendamento"));
        return new Avaliado(chefe.getId(), chefe.getNome(), TipoUsuario.CHEFE);
    }

    /** Só tags da lista do lado avaliado; repetidas contam uma vez. */
    private static Set<String> validarTags(List<String> tags, TipoUsuario avaliado) {
        Set<String> validas = new LinkedHashSet<>();
        if (tags == null) {
            return validas;
        }
        List<String> permitidas = (avaliado == TipoUsuario.CHEFE
                ? Arrays.stream(TagAvaliacaoChef.values()).map(Enum::name)
                : Arrays.stream(TagAvaliacaoCliente.values()).map(Enum::name)).toList();
        for (String tag : tags) {
            if (tag == null || !permitidas.contains(tag)) {
                throw new RegraDeNegocioException("Tag inválida para esta avaliação: " + tag);
            }
            validas.add(tag);
        }
        return validas;
    }

    /**
     * Soma direto no banco (atômica): avaliações simultâneas da mesma pessoa não perdem contagem.
     * A entidade já carregada nesta transação fica com os contadores antigos; nada a altera depois.
     */
    private void somarNoAvaliado(Avaliado avaliado, int nota) {
        int linhas = avaliado.tipo() == TipoUsuario.CHEFE
                ? chefeRepository.somarAvaliacao(avaliado.id(), nota)
                : usuarioRepository.somarAvaliacao(avaliado.id(), nota);
        if (linhas != 1) {
            throw new AgendamentoException();
        }
    }
}
