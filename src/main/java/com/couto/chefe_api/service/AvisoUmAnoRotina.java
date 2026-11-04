package com.couto.chefe_api.service;

import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.CandidatoAoAvisoDeUmAno;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Uma vez por dia avisa o chef de que falta até 30 dias para completar um ano de uma ocasião especial que ele
 * atendeu. Agrupa os candidatos por (chef, cliente) e chama o serviço uma vez por par, cada um numa transação
 * própria: uma falha é logada e não impede os demais.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvisoUmAnoRotina {

    private final AgendamentoRepository agendamentoRepository;
    private final AgendamentoService agendamentoService;
    private final Clock clock;

    private record Par(UUID chefeId, UUID clienteId) {}

    @Scheduled(cron = "${chefhome.rotina.aviso-um-ano.cron:0 0 9 * * *}")
    public void avisar() {
        LocalDateTime agora = LocalDateTime.now(clock);
        // a consulta pega um dia a mais de cada lado; o filtro exato (início + 1 ano) é em Java, por causa do 29/02
        List<CandidatoAoAvisoDeUmAno> candidatos = agendamentoRepository.candidatosAoAvisoDeUmAno(
                agora.minusYears(1).minusDays(1), agora.plusDays(AgendamentoService.JANELA_AVISO_UM_ANO_DIAS).minusYears(1).plusDays(1));
        Map<Par, List<Long>> porPar = new LinkedHashMap<>();
        for (CandidatoAoAvisoDeUmAno a : candidatos) {
            LocalDateTime aniversario = a.inicio().plusYears(1);
            if (aniversario.isAfter(agora) && !aniversario.isAfter(agora.plusDays(AgendamentoService.JANELA_AVISO_UM_ANO_DIAS))) {
                porPar.computeIfAbsent(new Par(a.chefeId(), a.clienteId()), p -> new ArrayList<>()).add(a.id());
            }
        }
        for (Map.Entry<Par, List<Long>> par : porPar.entrySet()) {
            try {
                agendamentoService.avisarUmAno(par.getValue());
            } catch (RuntimeException e) {
                log.warn("rotina: não avisou o chef {} sobre o cliente {}: {}", par.getKey().chefeId(), par.getKey().clienteId(), e.getMessage());
            }
        }
    }
}
