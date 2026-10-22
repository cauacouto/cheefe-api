package com.couto.chefe_api.service;

import com.couto.chefe_api.repository.AgendamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Conclui os confirmados que já terminaram e expira os pendentes que já começaram. Cada agendamento
 * numa transação própria (no AgendamentoService): uma falha é logada e não impede os demais.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgendamentoRotina {

    private final AgendamentoRepository agendamentoRepository;
    private final AgendamentoService agendamentoService;
    private final Clock clock;

    /** A cada 15 min por padrão; "-" desliga (perfil de teste: os testes chamam processar() direto). */
    @Scheduled(cron = "${chefhome.rotina.cron:0 */15 * * * *}")
    public void processar() {
        LocalDateTime agora = LocalDateTime.now(clock);
        for (Long id : agendamentoRepository.idsParaConcluir(agora)) {
            try {
                agendamentoService.concluirPeloSistema(id);
            } catch (RuntimeException e) {
                log.warn("rotina: não concluiu o agendamento {}: {}", id, e.getMessage());
            }
        }
        for (Long id : agendamentoRepository.idsParaExpirar(agora)) {
            try {
                agendamentoService.expirarPeloSistema(id);
            } catch (RuntimeException e) {
                log.warn("rotina: não expirou o agendamento {}: {}", id, e.getMessage());
            }
        }
    }
}
