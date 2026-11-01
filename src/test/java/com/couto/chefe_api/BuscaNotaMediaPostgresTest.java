package com.couto.chefe_api;

import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Menu;
import com.couto.chefe_api.dto.ChefeResponseDto;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.MenuRepository;
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
import java.util.Set;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** sort=notaMedia e sort=precoAPartirDe no Postgres real: divisão não inteira, "Novo" por último, menor preço dos menus ativos. */
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
    @Autowired MenuRepository menuRepository;

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
                PageRequest.of(0, 10, Sort.by("notaMedia")), PRECO, PRECO, null, null, null, null).getContent();

        assertThat(pagina).extracting(ChefeResponseDto::nome).containsExactly("Otimo", "Bom", "Medio", "Novo");
        assertThat(pagina.get(0).notaMedia()).isEqualByComparingTo("4.8");
        assertThat(pagina.get(3).notaMedia()).isNull();
    }

    @Test
    void ordenaPorPrecoAPartirDeNasDuasDirecoes() {
        chefe("Caro", 0, 0, "777.60");
        chefe("Barato", 0, 0, "777.10");
        ChefeModel medio = chefe("Medio", 0, 0, "777.90");
        menu(medio, "777.30"); // o menor entre os menus ativos é o que vale

        List<ChefeResponseDto> crescente = chefeService.mostrarChefes(
                PageRequest.of(0, 10, Sort.by("precoAPartirDe")), new BigDecimal("777"), new BigDecimal("777.99"),
                null, null, null, null).getContent();
        List<ChefeResponseDto> decrescente = chefeService.mostrarChefes(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "precoAPartirDe")), new BigDecimal("777"),
                new BigDecimal("777.99"), null, null, null, null).getContent();

        assertThat(crescente).extracting(ChefeResponseDto::nome).containsExactly("Barato", "Medio", "Caro");
        assertThat(crescente).extracting(ChefeResponseDto::precoAPartirDe)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("777.10"), new BigDecimal("777.30"), new BigDecimal("777.60"));
        assertThat(decrescente).extracting(ChefeResponseDto::nome).containsExactly("Caro", "Medio", "Barato");
        // a página seguinte e o total vêm sem a consulta de contagem ordenada
        var segunda = chefeService.mostrarChefes(PageRequest.of(1, 2, Sort.by("precoAPartirDe")),
                new BigDecimal("777"), new BigDecimal("777.99"), null, null, null, null);
        assertThat(segunda.getContent()).extracting(ChefeResponseDto::nome).containsExactly("Caro");
        assertThat(segunda.getTotalElements()).isEqualTo(3);
    }

    private void chefe(String nome, int soma, int total) {
        chefe(nome, soma, total, PRECO.toPlainString());
    }

    private ChefeModel chefe(String nome, int soma, int total, String preco) {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome(nome);
        chefe.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setEspecialidades(Set.of(Especialidades.BRASILEIRA));
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        chefe.setSomaNotas(soma);
        chefe.setTotalAvaliacoes(total);
        chefe = chefeRepository.save(TelefoneDeTeste.verificado(chefe));
        criados.add(chefe);
        menu(chefe, preco);
        return chefe;
    }

    /** Menu ativo sem pratos: a busca só olha o preço; ele some junto com o chef (ON DELETE CASCADE). */
    private void menu(ChefeModel chefe, String preco) {
        Menu menu = new Menu();
        menu.setChefe(chefe);
        menu.setNome("Menu");
        menu.setPrecoPorPessoa(new BigDecimal(preco));
        menu.setMinimoConvidados(1);
        menu.setMaximoConvidados(10);
        menu.setDuracaoMinutos(120);
        menu.setCriadoEm(LocalDateTime.now());
        menuRepository.save(menu);
    }
}
