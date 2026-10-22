package com.couto.chefe_api.service;

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
import java.util.ArrayList;
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
    private final Clock clock;

    @Transactional
    public AgendamentoResponseDto criarAgendamento(AgendamentoRequestDto dto, UUID usuarioId) {
        var user = usuarioRepository.findById(usuarioId).orElseThrow(UsuarioException::new);

        validarPeriodo(dto.getDataHoraInicial(), dto.getDataHoraFinal());
        List<ChefeModel> chefes = validarChefes(dto.getChefeIds());
        EnderecoModel endereco = buscarEndereco(dto.getEnderecoId(), usuarioId);

        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(user);
        agendamento.setEndereco(endereco);
        agendamento.setChefes(chefes);
        agendamento.setDataHoraInicial(dto.getDataHoraInicial());
        agendamento.setDataHoraFinal(dto.getDataHoraFinal());
        agendamento.setQuantidadePessoas(dto.getQuantidadePessoas());
        agendamento.setValorTotal(Agendamento.calcularValor(chefes, dto.getDataHoraInicial(), dto.getDataHoraFinal()));
        exigirHorarioLivre(agendamento);
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
     * Uma lista de chefes vazia é ignorada (não remove os chefes).
     */
    @Transactional
    public AgendamentoResponseDto atualizarAgendamento(AtualizarAgendamentoRequestDto dto, Long agendamentoId, UUID usuarioId) {
        // agendamento de outro cliente é tratado como inexistente
        Agendamento agenda = agendamentoRepository.findByIdAndUsuario_Id(agendamentoId, usuarioId)
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
            agenda.setChefes(validarChefes(dto.chefeIds()));
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

        if (recalcular) {
            agenda.setValorTotal(Agendamento.calcularValor(agenda.getChefes(), agenda.getDataHoraInicial(), agenda.getDataHoraFinal()));
            exigirHorarioLivre(agenda);
        }

        Agendamento salvo = agendamentoRepository.save(agenda);
        historico.registrar(salvo, AcaoAgendamento.EDITADO, usuarioId, TipoUsuario.CLIENTE, null, null);
        eventPublisher.publishEvent(new AgendamentoEditadoEvent(salvo.getId()));
        return agendamentoMapper.toDto(salvo);
    }

    @Transactional
    public void confirmarAgendamento(Long idAgendamento, UUID chefeId) {
        // só um chef do agendamento o enxerga; para os demais ele não existe
        Agendamento agendamento = agendamentoRepository.findById(idAgendamento)
                .filter(a -> a.getChefes().stream().anyMatch(chefe -> chefe.getId().equals(chefeId)))
                .orElseThrow(AgendamentoException::new);
        agendamento.getChefes().stream()
                .filter(chefe -> chefe.getId().equals(chefeId))
                .forEach(ChefeModel::exigirAtivo);
        if (agendamento.getStatus() != StatusAgendamento.PENDENTE) {
            throw new AgendamentoNaoConfirmavelException(agendamento.getStatus());
        }
        // confirmar vale para todos os chefs do agendamento: trava todos e confere a agenda de cada um
        chefeRepository.travar(agendamento.getChefes().stream().map(ChefeModel::getId).toList());
        agendamento.confirmar(agora());
        exigirHorarioLivre(agendamento);
        agendamentoRepository.save(agendamento);
        historico.registrar(agendamento, AcaoAgendamento.CONFIRMADO, chefeId, TipoUsuario.CHEFE, null, null);
        eventPublisher.publishEvent(new AgendamentoConfirmadoEvent(agendamento.getId(), chefeId));
    }

    @Transactional
    public void cancelarAgendamento(Long id, UUID clienteId, CancelamentoRequestDto dto) {
        // agendamento de outro cliente é tratado como inexistente
        Agendamento agendamento = agendamentoRepository.findByIdAndUsuario_Id(id, clienteId)
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
        ChefeModel chefe = chefeDoAgendamento(agendamento, chefeId);
        boolean restam = agendamento.recusar(chefe, agora());
        sairDoAgendamento(agendamento, chefe, AcaoAgendamento.RECUSADO, true, restam, dto);
    }

    @Transactional
    public void desistirDoAgendamento(Long id, UUID chefeId, RecusaRequestDto dto) {
        Agendamento agendamento = doChef(id, chefeId);
        ChefeModel chefe = chefeDoAgendamento(agendamento, chefeId);
        boolean restam = agendamento.desistir(chefe, agora());
        sairDoAgendamento(agendamento, chefe, AcaoAgendamento.DESISTENCIA, false, restam, dto);
    }

    @Transactional
    public void concluirAgendamento(Long id, UUID chefeId) {
        Agendamento agendamento = doChef(id, chefeId);
        chefeDoAgendamento(agendamento, chefeId);
        agendamento.concluirPeloChef(agora());
        historico.registrar(agendamento, AcaoAgendamento.CONCLUIDO, chefeId, TipoUsuario.CHEFE, null, null);
    }

    /** Linha do tempo: só o cliente dono ou um chef que ainda está no agendamento. */
    @Transactional(readOnly = true)
    public List<HistoricoAgendamentoDto> historico(Long id, UsuarioAutenticado usuario) {
        Agendamento agendamento = switch (usuario.tipo()) {
            case CLIENTE -> agendamentoRepository.findByIdAndUsuario_Id(id, usuario.id()).orElseThrow(AgendamentoException::new);
            case CHEFE -> doChef(id, usuario.id());
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

    private void sairDoAgendamento(Agendamento agendamento, ChefeModel chefe, AcaoAgendamento acao, boolean recusa,
                                   boolean restam, RecusaRequestDto dto) {
        MotivoAgendamento motivo = dto == null ? null : dto.motivo();
        String descricao = dto == null ? null : dto.descricao();
        historico.registrar(agendamento, acao, chefe.getId(), TipoUsuario.CHEFE, motivo, descricao);
        eventPublisher.publishEvent(new ChefSaiuDoAgendamentoEvent(agendamento.getId(), chefe.getNome(), recusa, restam,
                motivo, descricao));
    }

    /** Agendamento em que o chef está; para qualquer outro (inclusive quem já saiu dele) ele "não existe". */
    private Agendamento doChef(Long id, UUID chefeId) {
        return agendamentoRepository.findById(id)
                .filter(a -> a.getChefes().stream().anyMatch(chefe -> chefe.getId().equals(chefeId)))
                .orElseThrow(AgendamentoException::new);
    }

    /** O chef dentro do agendamento, exigindo conta ativa (apagado ou banido não age). */
    private static ChefeModel chefeDoAgendamento(Agendamento agendamento, UUID chefeId) {
        ChefeModel chefe = agendamento.getChefes().stream()
                .filter(c -> c.getId().equals(chefeId))
                .findFirst()
                .orElseThrow(AgendamentoException::new);
        chefe.exigirAtivo();
        return chefe;
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock);
    }

    /** Só agendamento CONFIRMADO ocupa a agenda do chef; pedidos pendentes podem se sobrepor. */
    private void exigirHorarioLivre(Agendamento agendamento) {
        Long proprio = agendamento.getId() == null ? -1L : agendamento.getId();
        agendamento.getChefes().stream()
                .filter(chefe -> agendamentoRepository.existeConfirmadoSobreposto(chefe.getId(),
                        agendamento.getDataHoraInicial(), agendamento.getDataHoraFinal(), proprio))
                .findFirst()
                .ifPresent(chefe -> {
                    throw new HorarioIndisponivelException(chefe.getNome());
                });
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
     * Garante que todos os ids existem (ids repetidos contam uma vez) e que os
     * chefes estão ativos e disponíveis.
     */
    private List<ChefeModel> validarChefes(List<UUID> chefeIds) {
        if (chefeIds == null || chefeIds.isEmpty()) {
            throw new AgendamentoInvalidoException("Informe ao menos um chefe");
        }

        Set<UUID> idsUnicos = new LinkedHashSet<>(chefeIds);
        List<ChefeModel> chefes = chefeRepository.findAllById(idsUnicos);

        if (chefes.size() != idsUnicos.size()) {
            Set<UUID> encontrados = new LinkedHashSet<>();
            chefes.forEach(chefe -> encontrados.add(chefe.getId()));
            idsUnicos.removeAll(encontrados);
            throw new ChefeNaoEncontradoException("chefe(s) não encontrado(s): " + idsUnicos);
        }

        boolean todosValidos = chefes.stream()
                .allMatch(chefe -> chefe.isDisponivel() && Boolean.TRUE.equals(chefe.getAtivo()) && !chefe.isSuspenso()
                        && denunciaRepository.countDistinctClienteByChefeIdAndStatus(chefe.getId(), StatusDenuncia.ABERTA)
                        < DenunciaService.LIMITE_OCULTACAO);
        if (!todosValidos) {
            throw new ChefeIndisponivelException();
        }
        if (!chefes.stream().allMatch(ChefeModel::isPerfilCompleto)) {
            throw new AgendamentoInvalidoException("chef com perfil incompleto");
        }
        return new ArrayList<>(chefes);
    }
}
