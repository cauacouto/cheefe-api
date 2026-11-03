package com.couto.chefe_api.service;

import com.couto.chefe_api.domain.AgendaBloqueio;
import com.couto.chefe_api.domain.AgendaDiaFixo;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.dto.AgendaChefeDto;
import com.couto.chefe_api.dto.BloqueioAgendaDto;
import com.couto.chefe_api.dto.BloqueioAgendaRequestDto;
import com.couto.chefe_api.dto.PeriodoIndisponivelDto;
import com.couto.chefe_api.exception.AgendaIndisponivelException;
import com.couto.chefe_api.exception.ChefeNaoEncontradoException;
import com.couto.chefe_api.exception.ConfirmadoNoPeriodoException;
import com.couto.chefe_api.exception.ConflitoException;
import com.couto.chefe_api.exception.RecursoNaoEncontradoException;
import com.couto.chefe_api.exception.RegraDeNegocioException;
import com.couto.chefe_api.repository.AgendaBloqueioRepository;
import com.couto.chefe_api.repository.AgendaDiaFixoRepository;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Quando o chef não atende: períodos avulsos e dias fixos da semana. Um horário [inicio, fim) de agendamento
 * conflita com um bloqueio que se sobreponha (encostar não conflita) ou com um dia fixo em que caia qualquer parte
 * dele. Mexer na agenda trava o chef, como a confirmação do agendamento, para não nascer um confirmado dentro de
 * um bloqueio recém-criado.
 */
@Service
@RequiredArgsConstructor
public class AgendaService {

    static final int MAXIMO_BLOQUEIOS_FUTUROS = 100;
    static final Duration DURACAO_MAXIMA_BLOQUEIO = Duration.ofDays(365);
    static final int JANELA_PADRAO_DIAS = 30;
    static final int JANELA_MAXIMA_DIAS = 90;

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final ChefeRepository chefeRepository;
    private final AgendaBloqueioRepository bloqueioRepository;
    private final AgendaDiaFixoRepository diaFixoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AgendaChefeDto agenda(UUID chefeId) {
        chefeRepository.findById(chefeId).orElseThrow(ChefeNaoEncontradoException::new).exigirAtivo();
        List<BloqueioAgendaDto> bloqueios = bloqueioRepository
                .findByChefe_IdAndFimAfterOrderByInicioAscIdAsc(chefeId, agora()).stream()
                .map(AgendaService::paraDto).toList();
        return new AgendaChefeDto(bloqueios, diasFixosOrdenados(chefeId));
    }

    @Transactional
    public BloqueioAgendaDto criarBloqueio(UUID chefeId, BloqueioAgendaRequestDto dto) {
        ChefeModel chefe = travarAtivo(chefeId);
        LocalDateTime agora = agora();
        validarBloqueio(dto.inicio(), dto.fim(), agora);

        // só o que ainda vai acontecer conta: um confirmado que já terminou espera a rotina concluí-lo
        List<Agendamento> confirmados = agendamentoRepository.confirmadosSobrepostos(chefeId,
                dto.inicio().isBefore(agora) ? agora : dto.inicio(), dto.fim());
        if (!confirmados.isEmpty()) {
            throw new ConfirmadoNoPeriodoException(descrever(confirmados));
        }
        if (bloqueioRepository.countByChefe_IdAndFimAfter(chefeId, agora) >= MAXIMO_BLOQUEIOS_FUTUROS) {
            throw new ConflitoException("Limite de " + MAXIMO_BLOQUEIOS_FUTUROS + " bloqueios futuros atingido");
        }

        AgendaBloqueio bloqueio = new AgendaBloqueio();
        bloqueio.setChefe(chefe);
        bloqueio.setInicio(dto.inicio());
        bloqueio.setFim(dto.fim());
        bloqueio.setCriadoEm(agora);
        return paraDto(bloqueioRepository.save(bloqueio));
    }

    @Transactional
    public void removerBloqueio(UUID chefeId, Long bloqueioId) {
        chefeRepository.findById(chefeId).orElseThrow(ChefeNaoEncontradoException::new).exigirAtivo();
        // de outro chef é tratado como inexistente
        AgendaBloqueio bloqueio = bloqueioRepository.findByIdAndChefe_Id(bloqueioId, chefeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("bloqueio não encontrado"));
        bloqueioRepository.delete(bloqueio);
    }

    /** Substitui a lista de dias fixos; só os dias novos precisam estar livres de agendamento confirmado futuro. */
    @Transactional
    public List<DayOfWeek> definirDiasFixos(UUID chefeId, List<DayOfWeek> dias) {
        ChefeModel chefe = travarAtivo(chefeId);
        Set<DayOfWeek> novos = EnumSet.noneOf(DayOfWeek.class);
        novos.addAll(dias);
        if (novos.size() != dias.size()) {
            throw new RegraDeNegocioException("Dias repetidos na lista de dias fixos");
        }

        List<AgendaDiaFixo> atuais = diaFixoRepository.doChefe(chefeId);
        Set<DayOfWeek> jaFixos = atuais.stream().map(AgendaDiaFixo::getDiaSemana).collect(Collectors.toSet());
        Set<DayOfWeek> adicionados = EnumSet.noneOf(DayOfWeek.class);
        adicionados.addAll(novos);
        adicionados.removeAll(jaFixos);

        if (!adicionados.isEmpty()) {
            List<Agendamento> pegos = agendamentoRepository.confirmadosFuturos(chefeId, agora()).stream()
                    .filter(a -> diasTocados(a.getDataHoraInicial(), a.getDataHoraFinal()).stream().anyMatch(adicionados::contains))
                    .toList();
            if (!pegos.isEmpty()) {
                throw new ConfirmadoNoPeriodoException(descrever(pegos));
            }
        }
        diaFixoRepository.deleteAll(atuais.stream().filter(d -> !novos.contains(d.getDiaSemana())).toList());
        diaFixoRepository.saveAll(adicionados.stream().map(dia -> new AgendaDiaFixo(chefe, dia)).toList());
        return novos.stream().sorted().toList();
    }

    /** Garante que o chef não está em bloqueio nem dia fixo no horário. */
    @Transactional(readOnly = true)
    public void exigirDisponivel(ChefeModel chefe, LocalDateTime inicio, LocalDateTime fim) {
        Set<DayOfWeek> tocados = diasTocados(inicio, fim);
        boolean emBloqueio = bloqueioRepository.existeSobreposto(chefe.getId(), inicio, fim);
        boolean emDiaFixo = !emBloqueio && diaFixoRepository.doChefe(chefe.getId()).stream()
                .anyMatch(d -> tocados.contains(d.getDiaSemana()));
        if (emBloqueio || emDiaFixo) {
            throw new AgendaIndisponivelException();
        }
    }

    /**
     * Períodos em que o chef não atende dentro da janela [de, ate] (dias inteiros): bloqueios, dias fixos e
     * confirmados, mesclados. Sem motivo nem id; quem não é visível na busca "não existe".
     */
    @Transactional(readOnly = true)
    public List<PeriodoIndisponivelDto> consultaPublica(UUID chefeId, LocalDate de, LocalDate ate) {
        if (!chefeRepository.visivel(chefeId)) {
            throw new ChefeNaoEncontradoException();
        }
        ChefeModel chefe = chefeRepository.findById(chefeId).orElseThrow(ChefeNaoEncontradoException::new);
        LocalDate inicioJanela = de != null ? de : LocalDate.now(clock);
        LocalDate fimJanela = ate != null ? ate : inicioJanela.plusDays(JANELA_PADRAO_DIAS);
        if (fimJanela.isBefore(inicioJanela)) {
            throw new RegraDeNegocioException("A data final deve ser igual ou posterior à inicial");
        }
        if (ChronoUnit.DAYS.between(inicioJanela, fimJanela) > JANELA_MAXIMA_DIAS) {
            throw new RegraDeNegocioException("A janela da consulta vai até " + JANELA_MAXIMA_DIAS + " dias");
        }
        LocalDateTime inicio = inicioJanela.atStartOfDay();
        LocalDateTime fim = fimJanela.plusDays(1).atStartOfDay();

        List<PeriodoIndisponivelDto> periodos = new ArrayList<>();
        bloqueioRepository.sobrepostos(chefeId, inicio, fim)
                .forEach(b -> periodos.add(new PeriodoIndisponivelDto(b.getInicio(), b.getFim())));
        // o confirmado ocupa também a folga do chef, antes e depois; a busca alarga a janela pela mesma folga
        Duration folga = chefe.folga();
        agendamentoRepository.confirmadosSobrepostos(chefeId, inicio.minus(folga), fim.plus(folga))
                .forEach(a -> periodos.add(new PeriodoIndisponivelDto(
                        a.getDataHoraInicial().minus(folga), a.getDataHoraFinal().plus(folga))));
        Set<DayOfWeek> fixos = diaFixoRepository.doChefe(chefeId).stream().map(AgendaDiaFixo::getDiaSemana)
                .collect(Collectors.toSet());
        for (LocalDate dia = inicioJanela; !dia.isAfter(fimJanela); dia = dia.plusDays(1)) {
            if (fixos.contains(dia.getDayOfWeek())) {
                periodos.add(new PeriodoIndisponivelDto(dia.atStartOfDay(), dia.plusDays(1).atStartOfDay()));
            }
        }
        return mesclar(periodos, inicio, fim);
    }

    /** Recorta na janela, ordena e junta o que se sobrepõe ou encosta. */
    private static List<PeriodoIndisponivelDto> mesclar(List<PeriodoIndisponivelDto> periodos, LocalDateTime inicio,
                                                        LocalDateTime fim) {
        List<PeriodoIndisponivelDto> recortados = periodos.stream()
                .map(p -> new PeriodoIndisponivelDto(
                        p.inicio().isBefore(inicio) ? inicio : p.inicio(), p.fim().isAfter(fim) ? fim : p.fim()))
                .sorted(Comparator.comparing(PeriodoIndisponivelDto::inicio))
                .toList();
        List<PeriodoIndisponivelDto> mesclados = new ArrayList<>();
        for (PeriodoIndisponivelDto atual : recortados) {
            int ultimo = mesclados.size() - 1;
            if (ultimo >= 0 && !atual.inicio().isAfter(mesclados.get(ultimo).fim())) {
                PeriodoIndisponivelDto anterior = mesclados.get(ultimo);
                if (atual.fim().isAfter(anterior.fim())) {
                    mesclados.set(ultimo, new PeriodoIndisponivelDto(anterior.inicio(), atual.fim()));
                }
            } else {
                mesclados.add(atual);
            }
        }
        return mesclados;
    }

    /** Dias da semana em que cai alguma parte de [inicio, fim); terminar à meia-noite não toca o dia seguinte. */
    static Set<DayOfWeek> diasTocados(LocalDateTime inicio, LocalDateTime fim) {
        Set<DayOfWeek> dias = new HashSet<>();
        LocalDate ultimo = fim.minusNanos(1).toLocalDate();
        for (LocalDate dia = inicio.toLocalDate(); !dia.isAfter(ultimo) && dias.size() < 7; dia = dia.plusDays(1)) {
            dias.add(dia.getDayOfWeek());
        }
        return dias;
    }

    private static void validarBloqueio(LocalDateTime inicio, LocalDateTime fim, LocalDateTime agora) {
        if (!fim.isAfter(inicio)) {
            throw new RegraDeNegocioException("O fim do bloqueio deve ser maior que o início");
        }
        if (!fim.isAfter(agora)) {
            throw new RegraDeNegocioException("O fim do bloqueio deve estar no futuro");
        }
        if (Duration.between(inicio, fim).compareTo(DURACAO_MAXIMA_BLOQUEIO) > 0) {
            throw new RegraDeNegocioException("O bloqueio pode durar até " + DURACAO_MAXIMA_BLOQUEIO.toDays() + " dias");
        }
    }

    /** Trava o chef (mesma trava da confirmação do agendamento) e exige conta ativa. */
    private ChefeModel travarAtivo(UUID chefeId) {
        ChefeModel chefe = chefeRepository.travar(List.of(chefeId)).stream().findFirst()
                .orElseThrow(ChefeNaoEncontradoException::new);
        chefe.exigirAtivo();
        return chefe;
    }

    private List<java.time.DayOfWeek> diasFixosOrdenados(UUID chefeId) {
        return diaFixoRepository.doChefe(chefeId).stream().map(AgendaDiaFixo::getDiaSemana).sorted().toList();
    }

    private static String descrever(List<Agendamento> agendamentos) {
        return agendamentos.stream()
                .map(a -> "#" + a.getId() + " (" + a.getDataHoraInicial().format(FORMATO) + " a "
                        + a.getDataHoraFinal().format(FORMATO) + ")")
                .collect(Collectors.joining("; "));
    }

    private static BloqueioAgendaDto paraDto(AgendaBloqueio bloqueio) {
        return new BloqueioAgendaDto(bloqueio.getId(), bloqueio.getInicio(), bloqueio.getFim());
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock);
    }
}
