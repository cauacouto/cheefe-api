package com.couto.chefe_api.domain;

import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.Set;
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
    @ValueSource(strings = {"nacionalidade", "especialidades", "senioridade", "anosDeExperiencia", "idiomas"})
    void incompletoSemCadaCampo(String campo) {
        ChefeModel chefe = completo();
        switch (campo) {
            case "nacionalidade" -> chefe.setNacionalidade(null);
            case "especialidades" -> chefe.setEspecialidades(Set.of());
            case "senioridade" -> chefe.setSenioridade(null);
            case "anosDeExperiencia" -> chefe.setAnosDeExperiencia(null);
            case "idiomas" -> chefe.setIdiomas(null);
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
                "nacionalidade", "especialidades", "senioridade", "anosDeExperiencia", "idiomas", "cidade", "estado"));
    }

    private static ChefeModel completo() {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome("Bia");
        chefe.setNacionalidade("Brasileira");
        chefe.setEspecialidades(Set.of(Especialidades.BRASILEIRA));
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setIdiomas("pt");
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        return chefe;
    }
}
