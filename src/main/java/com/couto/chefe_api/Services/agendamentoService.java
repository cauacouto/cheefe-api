package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.AgendamentoResponseDto;
import com.couto.chefe_api.Dtos.AgendamentoResquestDto;
import com.couto.chefe_api.Dtos.AtualizarAgendamentoRequestDto;
import com.couto.chefe_api.Dtos.ListaAgendamentoChefeDto;
import com.couto.chefe_api.Dtos.ListaAgendamentoUsuarioDto;
import com.couto.chefe_api.Excepitons.AgendamentoException;
import com.couto.chefe_api.Excepitons.AgendamentoInvalidoException;
import com.couto.chefe_api.Excepitons.AgendamentoNaoEditavelException;
import com.couto.chefe_api.Excepitons.ChefeIndisponivelException;
import com.couto.chefe_api.Excepitons.ChefeNaoEncontradoException;
import com.couto.chefe_api.Excepitons.EnderecoException;
import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.Mapper.AgendamentoMapper;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.repositorys.AgendamentoRepository;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.EndercoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class agendamentoService {

    private final UsuarioRepository usuarioRepository;
    private final ChefeRepository chefeRepository;
    private final AgendamentoMapper agendamentoMapper;
    private final EndercoRepository endercoRepository;
    private final AgendamentoRepository agendamentoRepository;

    @Transactional
    public AgendamentoResponseDto criarAgendamento(AgendamentoResquestDto dto, UUID usuarioId) {
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
        agendamento.setValorTotal(calcularValor(chefes, dto.getDataHoraInicial(), dto.getDataHoraFinal()));
        return agendamentoMapper.toDto(agendamentoRepository.save(agendamento));
    }

    @Transactional(readOnly = true)
    public List<ListaAgendamentoChefeDto> listarAgendamentosChefe(UUID chefeId) {
        return agendamentoMapper.toListChefe(agendamentoRepository.findAllByChefe(chefeId));
    }

    @Transactional(readOnly = true)
    public List<ListaAgendamentoUsuarioDto> listarAgendamentosUsuario(UUID usuarioId) {
        return agendamentoMapper.toListUser(agendamentoRepository.findAllByUsuario_Id(usuarioId));
    }

    /**
     * Atualização parcial: só os campos não nulos do DTO são aplicados.
     * Uma lista de chefes vazia é ignorada (não remove os chefes).
     */
    @Transactional
    public AgendamentoResponseDto atualizarAgedamento(AtualizarAgendamentoRequestDto dto, Long agendamentoId, UUID usuarioId) {
        // agendamento de outro cliente é tratado como inexistente
        Agendamento agenda = agendamentoRepository.findByIdAndUsuario_Id(agendamentoId, usuarioId)
                .orElseThrow(AgendamentoException::new);

        if (agenda.getStatus() != StatusAgendamento.PENDENDE) {
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
            agenda.setValorTotal(calcularValor(agenda.getChefes(), agenda.getDataHoraInicial(), agenda.getDataHoraFinal()));
        }

        return agendamentoMapper.toDto(agendamentoRepository.save(agenda));
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
        if (agendamento.getStatus() != StatusAgendamento.PENDENDE) {
            throw new AgendamentoNaoEditavelException(agendamento.getStatus());
        }
        agendamento.setStatus(StatusAgendamento.CONFIRMADO);
        agendamentoRepository.save(agendamento);
    }

    private EnderecoModel buscarEndereco(Long enderecoId, UUID usuarioId) {
        if (enderecoId == null) {
            throw new AgendamentoInvalidoException("Informe o endereço do agendamento");
        }
        return endercoRepository.findByIdAndUsuario_Id(enderecoId, usuarioId).orElseThrow(EnderecoException::new);
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
                .allMatch(chefe -> chefe.isDisponivel() && Boolean.TRUE.equals(chefe.getAtivo()));
        if (!todosValidos) {
            throw new ChefeIndisponivelException();
        }
        return new ArrayList<>(chefes);
    }

    private static BigDecimal calcularValor(List<ChefeModel> chefes, LocalDateTime inicio, LocalDateTime fim) {
        BigDecimal horas = BigDecimal.valueOf(ChronoUnit.MINUTES.between(inicio, fim))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        return chefes.stream()
                .map(ChefeModel::getValorHora)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(horas);
    }
}
