package com.couto.chefe_api.service;

import com.couto.chefe_api.exception.ForaDaCidadeException;

import com.couto.chefe_api.service.eventos.AgendamentoExpiradoEvent;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.repository.AgendamentoHistoricoRepository;
import com.couto.chefe_api.enums.MotivoRecusa;
import com.couto.chefe_api.enums.MotivoCancelamento;
import com.couto.chefe_api.dto.HistoricoAgendamentoDto;
import com.couto.chefe_api.domain.AgendamentoHistorico;
import com.couto.chefe_api.service.eventos.ChefSaiuDoAgendamentoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoCanceladoEvent;
import com.couto.chefe_api.enums.MotivoAgendamento;
import java.time.Clock;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.enums.AcaoAgendamento;
import com.couto.chefe_api.dto.RecusaRequestDto;
import com.couto.chefe_api.dto.CancelamentoRequestDto;
import com.couto.chefe_api.exception.HorarioIndisponivelException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import com.couto.chefe_api.dto.AgendamentoResponseDto;
import com.couto.chefe_api.dto.AgendamentoRequestDto;
import com.couto.chefe_api.dto.AtualizarAgendamentoRequestDto;
import com.couto.chefe_api.dto.ListaAgendamentoChefeDto;
import com.couto.chefe_api.dto.ListaAgendamentoUsuarioDto;
import com.couto.chefe_api.exception.AgendamentoException;
import com.couto.chefe_api.exception.AgendamentoInvalidoException;
import com.couto.chefe_api.exception.AgendamentoNaoConfirmavelException;
import com.couto.chefe_api.exception.AgendamentoNaoEditavelException;
import com.couto.chefe_api.exception.ChefeIndisponivelException;
import com.couto.chefe_api.exception.ChefeNaoEncontradoException;
import com.couto.chefe_api.exception.EnderecoException;
import com.couto.chefe_api.exception.UsuarioException;
import com.couto.chefe_api.mapper.AgendamentoMapper;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.DenunciaRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.service.eventos.AgendamentoConfirmadoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoCriadoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoEditadoEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgendamentoService {

    static final int TAMANHO_MAXIMO_PAGINA = 50;

    private final UsuarioRepository usuarioRepository;
    private final ChefeRepository chefeRepository;
    private final AgendamentoMapper agendamentoMapper;
    private final EnderecoRepository enderecoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DenunciaRepository denunciaRepository;
    private final HistoricoAgendamentoService historico;
    private final AgendamentoHistoricoRepository historicoRepository;
    private final AgendaService agendaService;
    private final Clock clock;

    @Transactional
    public AgendamentoResponseDto criarAgendamento(AgendamentoRequestDto dto, UUID usuarioId) {
        var user = usuarioRepository.findById(usuarioId).orElseThrow(UsuarioException::new);

        validarPeriodo(dto.getDataHoraInicial(), dto.getDataHoraFinal());
        ChefeModel chefe = validarChefe(dto.getChefeIds());
        EnderecoModel endereco = buscarEndereco(dto.getEnderecoId(), usuarioId);

        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(user);
        agendamento.setEndereco(endereco);
        agendamento.setChefe(chefe);
        agendamento.setDataHoraInicial(dto.getDataHoraInicial());
        agendamento.setDataHoraFinal(dto.getDataHoraFinal());
        agendamento.setQuantidadePessoas(dto.getQuantidadePessoas());
        agendamento.setCriadoEm(agora());
        agendamento.setValorTotal(Agendamento.calcularValor(chefe, dto.getDataHoraInicial(), dto.getDataHoraFinal()));
        exigirMesmaCidade(chefe, endereco);
        exigirHorarioLivre(agendamento);
        agendaService.exigirDisponivel(chefe, dto.getDataHoraInicial(), dto.getDataHoraFinal());
        Agendamento salvo = agendamentoRepository.save(agendamento);
        historico.registrar(salvo, AcaoAgendamento.CRIADO, usuarioId, TipoUsuario.CLIENTE, null, null);
        eventPublisher.publishEvent(new AgendamentoCriadoEvent(salvo.getId()));
        return agendamentoMapper.toDto(salvo);
    }

    @Transactional(readOnly = true)
    public Page<ListaAgendamentoChefeDto> listarAgendamentosChefe(UUID chefeId, Pageable pageable) {
        return agendamentoRepository.findRecebidos(chefeId, pagina(pageable)).map(agendamentoMapper::toChefeDto);
    }

    @Transactional(readOnly = true)
    public Page<ListaAgendamentoUsuarioDto> listarAgendamentosUsuario(UUID usuarioId, Pageable pageable) {
        return agendamentoRepository.findByUsuario_IdOrderByDataHoraInicialDescIdDesc(usuarioId, pagina(pageable))
                .map(agendamentoMapper::toUsuarioDto);
    }

    /** A ordem é sempre a do repositório (data mais distante primeiro); a vinda do cliente é ignorada. */
    private static PageRequest pagina(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), Math.max(1, Math.min(pageable.getPageSize(), TAMANHO_MAXIMO_PAGINA)));
    }

    /**
     * Atualização parcial: só os campos não nulos do DTO são aplicados.
     * Uma lista de chefes vazia é ignorada (não remove o chef).
     */
    @Transactional
    public AgendamentoResponseDto atualizarAgendamento(AtualizarAgendamentoRequestDto dto, Long agendamentoId, UUID usuarioId) {
        // agendamento de outro cliente é tratado como inexistente
        Agendamento agenda = agendamentoRepository.travarPorId(agendamentoId)
                .filter(a -> a.getUsuario() != null && a.getUsuario().getId().equals(usuarioId))
                .orElseThrow(AgendamentoException::new);

        if (agenda.getStatus() != StatusAgendamento.PENDENTE) {
            throw new AgendamentoNaoEditavelException(agenda.getStatus());
        }
        agenda.exigirEditavel(agora());

        if (dto.enderecoId() != null) {
            agenda.setEndereco(buscarEndereco(dto.enderecoId(), usuarioId));
        }

        boolean recalcular = false;

        if (dto.chefeIds() != null && !dto.chefeIds().isEmpty()) {
            agenda.setChefe(validarChefe(dto.chefeIds()));
            recalcular = true;
        }

        if (dto.dataHoraInicial() != null || dto.dataHoraFinal() != null) {
            LocalDateTime inicio = dto.dataHoraInicial() != null ? dto.dataHoraInicial() : agenda.getDataHoraInicial();
            LocalDateTime fim = dto.dataHoraFinal() != null ? dto.dataHoraFinal() : agenda.getDataHoraFinal();
            validarPeriodo(inicio, fim);
            agenda.setDataHoraInicial(inicio);
            agenda.setDataHoraFinal(fim);
            recalcular = true;
        }

        if (dto.quantidadePessoas() != null) {
            agenda.setQuantidadePessoas(dto.quantidadePessoas());
        }

        if (dto.enderecoId() != null || (dto.chefeIds() != null && !dto.chefeIds().isEmpty())) {
            exigirMesmaCidade(agenda.getChefe(), agenda.getEndereco());
        }

        if (recalcular) {
            agenda.setValorTotal(Agendamento.calcularValor(agenda.getChefe(), agenda.getDataHoraInicial(), agenda.getDataHoraFinal()));
            exigirHorarioLivre(agenda);
            agendaService.exigirDisponivel(agenda.getChefe(), agenda.getDataHoraInicial(), agenda.getDataHoraFinal());
        }

        Agendamento salvo = agendamentoRepository.save(agenda);
        historico.registrar(salvo, AcaoAgendamento.EDITADO, usuarioId, TipoUsuario.CLIENTE, null, null);
        eventPublisher.publishEvent(new AgendamentoEditadoEvent(salvo.getId()));
        return agendamentoMapper.toDto(salvo);
    }

    @Transactional
    public void confirmarAgendamento(Long idAgendamento, UUID chefeId) {
        // só o chef do agendamento o enxerga; para os demais ele não existe
        Agendamento agendamento = doChef(idAgendamento, chefeId);
        agendamento.getChefe().exigirAtivo();
        if (agendamento.getStatus() != StatusAgendamento.PENDENTE) {
            throw new AgendamentoNaoConfirmavelException(agendamento.getStatus());
        }
        // trava o chef e confere a agenda dele
        chefeRepository.travar(List.of(chefeId));
        agendamento.confirmar(agora());
        // o chef pode ter mudado de cidade depois do pedido
        exigirMesmaCidade(agendamento.getChefe(), agendamento.getEndereco());
        exigirHorarioLivre(agendamento);
        // a agenda do chef (como o horário livre); depois da trava, para não confirmar dentro de um bloqueio recém-criado
        agendaService.exigirDisponivel(agendamento.getChefe(),
                agendamento.getDataHoraInicial(), agendamento.getDataHoraFinal());
        agendamentoRepository.save(agendamento);
        historico.registrar(agendamento, AcaoAgendamento.CONFIRMADO, chefeId, TipoUsuario.CHEFE, null, null);
        eventPublisher.publishEvent(new AgendamentoConfirmadoEvent(agendamento.getId(), chefeId));
    }

    @Transactional
    public void cancelarAgendamento(Long id, UUID clienteId, CancelamentoRequestDto dto) {
        // com trava, como toda ação que muda o agendamento; de outro cliente é tratado como inexistente
        Agendamento agendamento = agendamentoRepository.travarPorId(id)
                .filter(a -> a.getUsuario() != null && a.getUsuario().getId().equals(clienteId))
                .orElseThrow(AgendamentoException::new);
        agendamento.cancelarPeloCliente(agora());
        MotivoAgendamento motivo = dto == null ? null : dto.motivo();
        String descricao = dto == null ? null : dto.descricao();
        historico.registrar(agendamento, AcaoAgendamento.CANCELADO, clienteId, TipoUsuario.CLIENTE, motivo, descricao);
        eventPublisher.publishEvent(new AgendamentoCanceladoEvent(agendamento.getId(), motivo, descricao));
    }

    @Transactional
    public void recusarAgendamento(Long id, UUID chefeId, RecusaRequestDto dto) {
        Agendamento agendamento = doChef(id, chefeId);
        ChefeModel chefe = chefeAtivo(agendamento);
        agendamento.recusar(agora());
        registrarSaida(agendamento, chefe, AcaoAgendamento.RECUSADO, true, dto);
    }

    @Transactional
    public void desistirDoAgendamento(Long id, UUID chefeId, RecusaRequestDto dto) {
        Agendamento agendamento = doChef(id, chefeId);
        ChefeModel chefe = chefeAtivo(agendamento);
        agendamento.desistir(agora());
        registrarSaida(agendamento, chefe, AcaoAgendamento.DESISTENCIA, false, dto);
    }

    @Transactional
    public void concluirAgendamento(Long id, UUID chefeId) {
        // com trava: a rotina pode estar concluindo o mesmo agendamento agora
        Agendamento agendamento = doChef(id, chefeId);
        chefeAtivo(agendamento);
        agendamento.concluirPeloChef(agora());
        historico.registrar(agendamento, AcaoAgendamento.CONCLUIDO, chefeId, TipoUsuario.CHEFE, null, null);
    }

    /** Rotina: relê com trava e só registra se a conclusão ainda se aplicava (rodar duas vezes não duplica). */
    @Transactional
    public void concluirPeloSistema(Long id) {
        agendamentoRepository.travarPorId(id)
                .filter(agendamento -> agendamento.concluirPeloSistema(agora()))
                .ifPresent(agendamento -> historico.registrar(agendamento, AcaoAgendamento.CONCLUIDO, null, null, null, null));
    }

    @Transactional
    public void expirarPeloSistema(Long id) {
        agendamentoRepository.travarPorId(id)
                .filter(agendamento -> agendamento.expirarPeloSistema(agora()))
                .ifPresent(agendamento -> {
                    historico.registrar(agendamento, AcaoAgendamento.EXPIRADO, null, null, null, null);
                    eventPublisher.publishEvent(new AgendamentoExpiradoEvent(agendamento.getId()));
                });
    }

    /** Linha do tempo: só o cliente dono ou o chef do agendamento (mesmo depois de recusar ou desistir). */
    @Transactional(readOnly = true)
    public List<HistoricoAgendamentoDto> historico(Long id, UsuarioAutenticado usuario) {
        Agendamento agendamento = switch (usuario.tipo()) {
            case CLIENTE -> agendamentoRepository.findByIdAndUsuario_Id(id, usuario.id()).orElseThrow(AgendamentoException::new);
            // leitura: sem trava (FOR UPDATE nem é permitido numa transação somente leitura no Postgres)
            case CHEFE -> agendamentoRepository.findById(id)
                    .filter(a -> a.getChefe().getId().equals(usuario.id()))
                    .orElseThrow(AgendamentoException::new);
            case MODERADOR -> throw new AgendamentoException();
        };
        List<AgendamentoHistorico> linhas = historicoRepository.findByAgendamento_IdOrderByOcorridoEmAscIdAsc(agendamento.getId());
        Map<UUID, String> nomes = nomesDosAutores(linhas);
        return linhas.stream().map(linha -> new HistoricoAgendamentoDto(
                linha.getAcao(),
                linha.getAutorId() == null ? null
                        : new HistoricoAgendamentoDto.Autor(linha.getAutorId(), nomes.get(linha.getAutorId()), linha.getAutorTipo()),
                motivoDe(linha.getMotivo()),
                linha.getDescricao(),
                linha.getOcorridoEm())).toList();
    }

    /** O nome é resolvido na leitura (o histórico guarda só o id), buscando cada tipo de autor de uma vez. */
    private Map<UUID, String> nomesDosAutores(List<AgendamentoHistorico> linhas) {
        Map<UUID, String> nomes = new HashMap<>();
        Set<UUID> clientes = linhas.stream().filter(l -> l.getAutorTipo() == TipoUsuario.CLIENTE)
                .map(AgendamentoHistorico::getAutorId).collect(Collectors.toSet());
        Set<UUID> chefes = linhas.stream().filter(l -> l.getAutorTipo() == TipoUsuario.CHEFE)
                .map(AgendamentoHistorico::getAutorId).collect(Collectors.toSet());
        usuarioRepository.findAllById(clientes).forEach(u -> nomes.put(u.getId(), u.getNome()));
        chefeRepository.findAllById(chefes).forEach(c -> nomes.put(c.getId(), c.getNome()));
        return nomes;
    }

    private static HistoricoAgendamentoDto.Motivo motivoDe(String codigo) {
        if (codigo == null) {
            return null;
        }
        MotivoAgendamento motivo = Arrays.stream(MotivoCancelamento.values()).filter(m -> m.name().equals(codigo))
                .<MotivoAgendamento>map(m -> m).findFirst()
                .orElseGet(() -> MotivoRecusa.valueOf(codigo));
        return new HistoricoAgendamentoDto.Motivo(motivo.name(), motivo.rotulo());
    }

    private void registrarSaida(Agendamento agendamento, ChefeModel chefe, AcaoAgendamento acao, boolean recusa,
                                RecusaRequestDto dto) {
        MotivoAgendamento motivo = dto == null ? null : dto.motivo();
        String descricao = dto == null ? null : dto.descricao();
        historico.registrar(agendamento, acao, chefe.getId(), TipoUsuario.CHEFE, motivo, descricao);
        eventPublisher.publishEvent(new ChefSaiuDoAgendamentoEvent(agendamento.getId(), chefe.getNome(), recusa, motivo, descricao));
    }

    /**
     * Agendamento do chef, com trava (duas ações ao mesmo tempo esperam uma pela outra em vez de
     * sobrescrever); para qualquer outro chef ele "não existe". O chef continua dono depois de recusar ou desistir.
     */
    private Agendamento doChef(Long id, UUID chefeId) {
        return agendamentoRepository.travarPorId(id)
                .filter(a -> a.getChefe().getId().equals(chefeId))
                .orElseThrow(AgendamentoException::new);
    }

    /** O chef do agendamento, exigindo conta ativa (apagado ou banido não age). */
    private static ChefeModel chefeAtivo(Agendamento agendamento) {
        ChefeModel chefe = agendamento.getChefe();
        chefe.exigirAtivo();
        return chefe;
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock);
    }

    /** Só agendamento CONFIRMADO ocupa a agenda do chef; pedidos pendentes podem se sobrepor. */
    private void exigirHorarioLivre(Agendamento agendamento) {
        Long proprio = agendamento.getId() == null ? -1L : agendamento.getId();
        ChefeModel chefe = agendamento.getChefe();
        if (agendamentoRepository.existeConfirmadoSobreposto(chefe.getId(),
                agendamento.getDataHoraInicial(), agendamento.getDataHoraFinal(), proprio)) {
            throw new HorarioIndisponivelException(chefe.getNome());
        }
    }

    /** O chef só atende na cidade de origem dele: contra a cidade e a UF do endereço. */
    private static void exigirMesmaCidade(ChefeModel chefe, EnderecoModel endereco) {
        if (!chefe.atendeEm(endereco.getCidade(), endereco.getEstado())) {
            throw new ForaDaCidadeException(chefe.getNome(), endereco.getCidade());
        }
    }

    private EnderecoModel buscarEndereco(Long enderecoId, UUID usuarioId) {
        if (enderecoId == null) {
            throw new AgendamentoInvalidoException("Informe o endereço do agendamento");
        }
        return enderecoRepository.findByIdAndUsuario_Id(enderecoId, usuarioId).orElseThrow(EnderecoException::new);
    }

    private static void validarPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null) {
            throw new AgendamentoInvalidoException("Informe a data/hora inicial e final");
        }
        if (!fim.isAfter(inicio)) {
            throw new AgendamentoInvalidoException("data/hora final deve ser maior que a inicial");
        }
    }

    /**
     * O agendamento tem um chef só: ids repetidos contam uma vez, mais de um id distinto é rejeitado.
     * Garante que o chef existe, está ativo e disponível.
     */
    private ChefeModel validarChefe(List<UUID> chefeIds) {
        if (chefeIds == null || chefeIds.isEmpty()) {
            throw new AgendamentoInvalidoException("Informe o chefe");
        }

        Set<UUID> idsUnicos = new LinkedHashSet<>(chefeIds);
        if (idsUnicos.size() > 1) {
            throw new AgendamentoInvalidoException("Um agendamento é feito com um chef só");
        }
        ChefeModel chefe = chefeRepository.findAllById(idsUnicos).stream().findFirst()
                .orElseThrow(() -> new ChefeNaoEncontradoException("chefe não encontrado: " + idsUnicos));

        boolean valido = chefe.isDisponivel() && Boolean.TRUE.equals(chefe.getAtivo()) && !chefe.isSuspenso()
                && chefe.getBloqueadoEm() == null
                && denunciaRepository.countDistinctClienteByChefeIdAndStatus(chefe.getId(), StatusDenuncia.ABERTA)
                < DenunciaService.LIMITE_OCULTACAO;
        if (!valido) {
            throw new ChefeIndisponivelException();
        }
        if (!chefe.isPerfilCompleto()) {
            throw new AgendamentoInvalidoException("chef com perfil incompleto");
        }
        return chefe;
    }
}
