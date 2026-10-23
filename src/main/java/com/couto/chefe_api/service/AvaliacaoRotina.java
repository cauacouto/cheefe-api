package com.couto.chefe_api.service;

import com.couto.chefe_api.repository.AvaliacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Publica o comentário com nome que esperou os 7 dias sem o chef avaliar o cliente. Cada um numa
 * transação própria (no AvaliacaoService): uma falha é logada e não impede os demais.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvaliacaoRotina {

    private final AvaliacaoRepository avaliacaoRepository;
    private final AvaliacaoService avaliacaoService;
    private final Clock clock;

    /** A cada 15 min por padrão; "-" desliga (perfil de teste: os testes chamam publicar() direto). */
    @Scheduled(cron = "${chefhome.rotina.cron:0 */15 * * * *}")
    public void publicar() {
        LocalDateTime agora = LocalDateTime.now(clock);
        for (Long id : avaliacaoRepository.idsComNomeParaPublicar(agora.minus(AvaliacaoService.PRAZO))) {
            try {
                avaliacaoService.publicarComNome(id, agora);
            } catch (RuntimeException e) {
                log.warn("rotina: não publicou a avaliação {}: {}", id, e.getMessage());
            }
        }
    }
}
