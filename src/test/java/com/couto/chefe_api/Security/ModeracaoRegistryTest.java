package com.couto.chefe_api.Security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModeracaoRegistryTest {

    @Test
    void reconheceComEspacosEMaiusculas() {
        ModeracaoRegistry registry = new ModeracaoRegistry(" Moderacao@ChefHome.test , outra@x.com");

        assertThat(registry.ehModerador("moderacao@chefhome.test")).isTrue();
        assertThat(registry.ehModerador("  OUTRA@x.com ")).isTrue();
        assertThat(registry.ehModerador("cliente@x.com")).isFalse();
    }

    @Test
    void idEhEstavelIndependenteDaCaixa() {
        ModeracaoRegistry registry = new ModeracaoRegistry("moderacao@chefhome.test");

        assertThat(registry.idDe("Moderacao@ChefHome.test")).isEqualTo(registry.idDe("moderacao@chefhome.test"));
        assertThat(registry.ids()).containsExactly(registry.idDe("moderacao@chefhome.test"));
    }

    @Test
    void configuracaoVaziaNaoTemModeradores() {
        ModeracaoRegistry registry = new ModeracaoRegistry("");

        assertThat(registry.ids()).isEmpty();
        assertThat(registry.ehModerador("")).isFalse();
        assertThat(registry.ehModerador(null)).isFalse();
    }
}
