package com.couto.chefe_api.dto;

import com.couto.chefe_api.domain.Menu;
import com.couto.chefe_api.domain.MenuItem;
import com.couto.chefe_api.enums.EtapaMenu;
import com.couto.chefe_api.enums.OcasiaoMenu;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public record MenuResponseDto(
        Long id,
        String nome,
        String descricao,
        BigDecimal precoPorPessoa,
        int minimoConvidados,
        int maximoConvidados,
        int duracaoMinutos,
        boolean ingredientesInclusos,
        boolean ativo,
        List<OcasiaoMenu> ocasioes,
        List<Item> itens
) {

    public record Item(Integer pratoId, String nomePrato, String descricao, String imageUrl, EtapaMenu etapa, int ordem) {
    }

    /** Ocasiões na ordem do enum e itens por etapa e ordem (o prato como desempate, para a ordem ser estável). */
    public static MenuResponseDto de(Menu menu) {
        List<Item> itens = menu.getItens().stream()
                .sorted(Comparator.comparing(MenuItem::getEtapa).thenComparingInt(MenuItem::getOrdem)
                        .thenComparing(i -> i.getPrato().getId()))
                .map(i -> new Item(i.getPrato().getId(), i.getPrato().getNomePrato(), i.getPrato().getDescricao(),
                        i.getPrato().getImageUrl(), i.getEtapa(), i.getOrdem()))
                .toList();
        return new MenuResponseDto(menu.getId(), menu.getNome(), menu.getDescricao(), menu.getPrecoPorPessoa(),
                menu.getMinimoConvidados(), menu.getMaximoConvidados(), menu.getDuracaoMinutos(),
                menu.isIngredientesInclusos(), menu.isAtivo(), menu.getOcasioes().stream().sorted().toList(), itens);
    }
}
