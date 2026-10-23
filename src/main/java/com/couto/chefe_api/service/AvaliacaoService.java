package com.couto.chefe_api.service;

import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.Avaliacao;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.dto.AvaliacaoPendenteDto;
import com.couto.chefe_api.dto.AvaliarRequestDto;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TagAvaliacaoChef;
import com.couto.chefe_api.enums.TagAvaliacaoCliente;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.exception.AgendamentoException;
import com.couto.chefe_api.exception.AvaliacaoForaDoPrazoException;
import com.couto.chefe_api.exception.ConflitoException;
import com.couto.chefe_api.exception.RegraDeNegocioException;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.AvaliacaoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Avaliações depois de um agendamento concluído: o cliente avalia cada chef e cada chef avalia o
 * cliente, até 7 dias depois da conclusão. A média é anônima (só contadores no avaliado).
 */
@Service
@RequiredArgsConstructor
public class AvaliacaoService {

    static final Duration PRAZO = Duration.ofDays(7);
    static final String MENSAGEM_RECEBIDA = "Você recebeu uma nova avaliação";

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
