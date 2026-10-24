package com.couto.chefe_api;

import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.repository.ChefeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uma gravação comum (ex.: foto do perfil) que leu o chef antes de uma avaliação ou de um bloqueio
 * automático não pode devolver os valores antigos: só as colunas alteradas vão no UPDATE.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class GravacaoParcialPostgresTest {

    @DynamicPropertySource
    static void bancoReal(DynamicPropertyRegistry registry) throws Exception {
        MigracoesPostgresTest.configurarBancoReal(registry);
    }

    @Autowired ChefeRepository chefeRepository;
    @Autowired PlatformTransactionManager transactionManager;

    ChefeModel bia;

    @AfterEach
    void limparBanco() {
        if (bia != null) {
            chefeRepository.deleteById(bia.getId());
        }
    }

    @Test
    void gravacaoQueLeuAntesNaoDesfazAvaliacaoNemBloqueio() {
        bia = novoChefe();
        TransactionTemplate foto = new TransactionTemplate(transactionManager);
        TransactionTemplate outra = new TransactionTemplate(transactionManager);
        outra.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        foto.executeWithoutResult(st -> {
            ChefeModel lida = chefeRepository.findById(bia.getId()).orElseThrow();
            // enquanto a foto sobe: uma avaliação e um bloqueio automático commitam
            outra.executeWithoutResult(st2 -> chefeRepository.somarAvaliacao(bia.getId(), 5));
            outra.executeWithoutResult(st2 -> {
                ChefeModel bloqueada = chefeRepository.findById(bia.getId()).orElseThrow();
                bloqueada.setBloqueadoEm(LocalDateTime.now());
            });
            lida.setImageUrl("https://img.test/nova.jpg");
        });

        ChefeModel depois = chefeRepository.findById(bia.getId()).orElseThrow();
        assertThat(depois.getImageUrl()).isEqualTo("https://img.test/nova.jpg");
        assertThat(depois.getTotalAvaliacoes()).isEqualTo(1);
        assertThat(depois.getSomaNotas()).isEqualTo(5);
        assertThat(depois.getBloqueadoEm()).isNotNull();
    }

    private ChefeModel novoChefe() {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome("Bia");
        chefe.setEmail("bia-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("100"));
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        return chefeRepository.save(chefe);
    }
}
