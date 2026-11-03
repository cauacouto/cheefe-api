package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.OcasiaoMenu;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Menu do chef. Múltiplo de 30 minutos, máximo >= mínimo, sem ocasião ou prato repetido, prato do próprio chef
 * e contato no texto são validados no serviço.
 */
public record MenuRequestDto(
        @NotBlank @Size(max = 100) String nome,
        @Size(max = 1000) String descricao,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal precoPorPessoa,
        @NotNull @Min(1) @Max(50) Integer minimoConvidados,
        @NotNull @Min(1) @Max(50) Integer maximoConvidados,
        @NotNull @Min(60) @Max(720) Integer duracaoMinutos,
        @NotNull Boolean ingredientesInclusos,
        @NotEmpty List<@NotNull OcasiaoMenu> ocasioes,
        @NotEmpty @Size(max = 15) List<@NotNull @Valid MenuItemRequestDto> itens
) {
}
