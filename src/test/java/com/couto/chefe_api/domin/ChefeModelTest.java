package com.couto.chefe_api.domin;

import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChefeModelTest {

    @Test
    void completoComTodosOsCampos() {
        ChefeModel chefe = completo();

        assertThat(chefe.isPerfilCompleto()).isTrue();
        assertThat(chefe.camposPendentes()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nacionalidade", "especialidade", "senioridade", "anosDeExperiencia", "idiomas", "valorHora"})
    void incompletoSemCadaCampo(String campo) {
        ChefeModel chefe = completo();
        switch (campo) {
            case "nacionalidade" -> chefe.setNacionalidade(null);
            case "especialidade" -> chefe.setEspecialidade(null);
            case "senioridade" -> chefe.setSenioridade(null);
            case "anosDeExperiencia" -> chefe.setAnosDeExperiencia(null);
            case "idiomas" -> chefe.setIdiomas(null);
            case "valorHora" -> chefe.setValorHora(null);
            default -> throw new IllegalArgumentException(campo);
        }

        assertThat(chefe.isPerfilCompleto()).isFalse();
        assertThat(chefe.camposPendentes()).containsExactly(campo);
    }

    @Test
    void idiomasEmBrancoContaComoPendente() {
        ChefeModel chefe = completo();
        chefe.setIdiomas("   ");

        assertThat(chefe.isPerfilCompleto()).isFalse();
        assertThat(chefe.camposPendentes()).containsExactly("idiomas");
    }

    @Test
    void zeroAnosDeExperienciaContaComoInformado() {
        ChefeModel chefe = completo();
        chefe.setAnosDeExperiencia(0);

        assertThat(chefe.isPerfilCompleto()).isTrue();
    }

    @Test
    void pendentesNaOrdemDoContrato() {
        assertThat(new ChefeModel().camposPendentes()).isEqualTo(List.of(
                "nacionalidade", "especialidade", "senioridade", "anosDeExperiencia", "idiomas", "valorHora"));
    }

    private static ChefeModel completo() {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome("Bia");
        chefe.setNacionalidade("Brasileira");
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("150"));
        return chefe;
    }
}
