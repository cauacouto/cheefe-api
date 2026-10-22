package com.couto.chefe_api.service;

import com.couto.chefe_api.enums.CategoriaConteudo;
import com.couto.chefe_api.service.FiltroConteudo.ResultadoFiltro;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FiltroConteudoTest {

    private final FiltroConteudo filtro = new FiltroConteudo(Map.of(
            CategoriaConteudo.OFENSA, List.of("palavrao", "bobo alegre"),
            CategoriaConteudo.ODIO, List.of("odiento"),
            CategoriaConteudo.AMEACA, List.of("vou te pegar", "mata")));

    @Test
    void ofensaViraAsteriscos() {
        ResultadoFiltro r = filtro.analisar("Que palavrao feio");
        assertThat(r.textoExibido()).isEqualTo("Que *** feio");
        assertThat(r.violacao()).isEmpty();
    }

    @Test
    void ignoraMaiusculasAcentosEPontuacao() {
        assertThat(filtro.analisar("PALAVRÃO!").textoExibido()).isEqualTo("***!");
        assertThat(filtro.analisar("(Palavrao)").textoExibido()).isEqualTo("(***)");
    }

    @Test
    void naoPegaPedacoDePalavra() {
        ResultadoFiltro r = filtro.analisar("tomate e matar a fome");
        assertThat(r.violacao()).isEmpty();
        assertThat(r.textoExibido()).isEqualTo("tomate e matar a fome");
    }

    @Test
    void expressaoDeVariasPalavras() {
        assertThat(filtro.analisar("ele é bobo alegre").textoExibido()).isEqualTo("ele é ***");
        ResultadoFiltro r = filtro.analisar("Vou   te pegar");
        assertThat(r.violacao()).hasValueSatisfying(v -> {
            assertThat(v.categoria()).isEqualTo(CategoriaConteudo.AMEACA);
            assertThat(v.termo()).isEqualTo("vou te pegar");
        });
    }

    @Test
    void odioTemPrioridade() {
        ResultadoFiltro r = filtro.analisar("odiento palavrao");
        assertThat(r.violacao()).hasValueSatisfying(v ->
                assertThat(v.categoria()).isEqualTo(CategoriaConteudo.ODIO));
    }

    @Test
    void nuloOuBranco() {
        assertThat(filtro.analisar(null).textoExibido()).isNull();
        assertThat(filtro.analisar(null).violacao()).isEmpty();
        assertThat(filtro.analisar("   ").textoExibido()).isEqualTo("   ");
        assertThat(filtro.analisar("   ").violacao()).isEmpty();
    }

    @Test
    void listaDeProducaoCarregaAsTresCategorias() {
        FiltroConteudo producao = new FiltroConteudo(new ClassPathResource("moderacao/termos.yml"));
        for (CategoriaConteudo c : CategoriaConteudo.values()) {
            assertThat(producao.termos().get(c)).as(c.name()).isNotEmpty();
        }
        assertThat(producao.analisar("esse tempero assassino vai de matar, vou atacar o prato").violacao()).isEmpty();
    }
}
