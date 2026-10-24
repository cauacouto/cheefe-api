package com.couto.chefe_api;

import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.dto.ChefeResponseDto;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.service.ChefeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** sort=notaMedia no Postgres real: divisão não inteira e "Novo" (média nula) por último. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class BuscaNotaMediaPostgresTest {

    /** Preço que só os chefs deste teste têm: o banco é compartilhado com o MigracoesPostgresTest. */
    static final BigDecimal PRECO = new BigDecimal("777.77");

    @DynamicPropertySource
    static void bancoReal(DynamicPropertyRegistry registry) throws Exception {
        MigracoesPostgresTest.configurarBancoReal(registry);
    }

    @Autowired ChefeService chefeService;
    @Autowired ChefeRepository chefeRepository;

    final List<ChefeModel> criados = new ArrayList<>();

    @AfterEach
    void limparBanco() {
        chefeRepository.deleteAll(criados);
    }

    @Test
    void maiorMediaPrimeiroENovoPorUltimo() {
        chefe("Novo", 20, 4);
        chefe("Bom", 22, 5);   // 4.4
        chefe("Otimo", 24, 5); // 4.8: com divisão inteira empataria com "Bom" (4)
        chefe("Medio", 21, 5); // 4.2

        List<ChefeResponseDto> pagina = chefeService.mostrarChefes(
                PageRequest.of(0, 10, Sort.by("notaMedia")), PRECO, PRECO, null, null, null).getContent();

        assertThat(pagina).extracting(ChefeResponseDto::nome).containsExactly("Otimo", "Bom", "Medio", "Novo");
        assertThat(pagina.get(0).notaMedia()).isEqualByComparingTo("4.8");
        assertThat(pagina.get(3).notaMedia()).isNull();
    }

    private void chefe(String nome, int soma, int total) {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome(nome);
        chefe.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(PRECO);
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        chefe.setSomaNotas(soma);
        chefe.setTotalAvaliacoes(total);
        criados.add(chefeRepository.save(TelefoneDeTeste.verificado(chefe)));
    }
}
