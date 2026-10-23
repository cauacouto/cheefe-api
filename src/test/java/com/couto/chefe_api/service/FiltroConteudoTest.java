package com.couto.chefe_api.service;

import com.couto.chefe_api.enums.CategoriaConteudo;
import com.couto.chefe_api.service.FiltroConteudo.ResultadoFiltro;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        for (String ok : List.of("vou te encontrar no mercado", "vou explodir de tanto comer",
                "a carne estava de matar", "vou te pegar na escola", "o macaco comeu a banana")) {
            assertThat(producao.analisar(ok).violacao()).as(ok).isEmpty();
        }
        for (String benigno : List.of("deixou o fogão preto imundo de gordura", "vou te matar de tanto comer",
                "achei uma bicha nojenta na alface", "carne de raça inferior", "não levou o lixo embora")) {
            ResultadoFiltro r = producao.analisar(benigno);
            assertThat(r.violacao()).as(benigno).isEmpty();
            assertThat(r.textoExibido()).as(benigno).isEqualTo(benigno);
        }
        assertThat(producao.analisar("esse tempero assassino vai de matar, vou atacar o prato").violacao()).isEmpty();
    }

    @Test
    void termoMaisLongoVenceIndependenteDaOrdem() {
        for (List<String> ordem : List.of(List.of("xxx", "xxx yyy"), List.of("xxx yyy", "xxx"))) {
            FiltroConteudo f = new FiltroConteudo(Map.of(CategoriaConteudo.OFENSA, ordem));
            assertThat(f.analisar("a xxx yyy b").textoExibido()).isEqualTo("a *** b");
            assertThat(f.analisar("xxx yyy xxx").textoExibido()).isEqualTo("*** ***");
        }
    }

    @Test
    void textoDecompostoNFDEhReconhecido() {
        String nfdOfensa = java.text.Normalizer.normalize("PALAVRÃO!", java.text.Normalizer.Form.NFD);
        assertThat(filtro.analisar(nfdOfensa).textoExibido()).isEqualTo("***!");
        FiltroConteudo f = new FiltroConteudo(Map.of(CategoriaConteudo.ODIO, List.of("ódiento")));
        String nfdOdio = java.text.Normalizer.normalize("ÓDIENTO", java.text.Normalizer.Form.NFD);
        assertThat(f.analisar(nfdOdio).violacao()).hasValueSatisfying(v ->
                assertThat(v.categoria()).isEqualTo(CategoriaConteudo.ODIO));
    }

    @Test
    void ameacaTemPrioridadeSobreOfensa() {
        assertThat(filtro.analisar("palavrao vou te pegar").violacao()).hasValueSatisfying(v ->
                assertThat(v.categoria()).isEqualTo(CategoriaConteudo.AMEACA));
    }

    @Test
    void recusaTermoComMenosDeTresCaracteres() {
        // "***" no lugar de um termo curto faria o texto exibido passar do limite da coluna
        for (CategoriaConteudo c : CategoriaConteudo.values()) {
            for (String curto : List.of("ab", "a", "  x ", "!!", "")) {
                assertThatThrownBy(() -> new FiltroConteudo(Map.of(c, List.of("palavrao", curto))))
                        .as(c + " '" + curto + "'")
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("3");
            }
        }
        // três caracteres bastam, também no total de uma expressão
        assertThat(new FiltroConteudo(Map.of(CategoriaConteudo.OFENSA, List.of("bob", "a b"))).analisar("seu bob").textoExibido())
                .isEqualTo("seu ***");
    }
}
