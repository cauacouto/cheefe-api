package com.couto.chefe_api;

import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Menu;
import com.couto.chefe_api.domain.MenuItem;
import com.couto.chefe_api.domain.Pratos;
import com.couto.chefe_api.enums.EtapaMenu;
import com.couto.chefe_api.enums.OcasiaoMenu;
import com.couto.chefe_api.repository.MenuRepository;
import com.couto.chefe_api.repository.PratosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Menu ativo pronto para os testes, com um prato próprio do chef. Padrão: R$ 100,00 por pessoa, de 1 a 50
 * convidados, 120 minutos, ingredientes inclusos e a ocasião {@code OUTRA}. Quem usa limpa os pratos
 * ({@code pratosRepository.deleteAll()}) antes de apagar o chef.
 */
@Component
@RequiredArgsConstructor
public class MenuDeTeste {

    private final MenuRepository menuRepository;
    private final PratosRepository pratosRepository;

    public Menu ativo(ChefeModel chefe) {
        return ativo(chefe, new BigDecimal("100.00"));
    }

    public Menu ativo(ChefeModel chefe, BigDecimal preco, OcasiaoMenu... ocasioes) {
        Pratos prato = new Pratos();
        prato.setNomePrato("Prato do menu");
        prato.setDescricao("Prato de teste");
        prato.setChefeModel(chefe);
        prato = pratosRepository.save(prato);

        Menu menu = new Menu();
        menu.setChefe(chefe);
        menu.setNome("Menu de teste");
        menu.setPrecoPorPessoa(preco);
        menu.setMinimoConvidados(1);
        menu.setMaximoConvidados(50);
        menu.setDuracaoMinutos(120);
        menu.setIngredientesInclusos(true);
        menu.setAtivo(true);
        menu.setCriadoEm(LocalDateTime.now());
        menu.getOcasioes().addAll(ocasioes.length == 0 ? List.of(OcasiaoMenu.OUTRA) : Arrays.asList(ocasioes));
        menu.getItens().add(new MenuItem(menu, prato, EtapaMenu.PRINCIPAL, 1));
        return menuRepository.save(menu);
    }
}
