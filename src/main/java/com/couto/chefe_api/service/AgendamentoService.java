package com.couto.chefe_api.service;

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
        exigirHorarioLivre(agendamento);
        agendamento.setStatus(StatusAgendamento.CONFIRMADO);
        agendamentoRepository.save(agendamento);
        eventPublisher.publishEvent(new AgendamentoConfirmadoEvent(agendamento.getId(), chefeId));
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
