package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.TipoDeResidencia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * O complemento é obrigatório só para apartamento; essa regra fica em
 * {@link TipoDeResidencia#validar}.
 */
public record EnderecoRequestDto(
        @NotBlank @Size(max = 150) String rua,
        @NotBlank @Size(max = 20) String numero,
        @Size(max = 100) String complemento,
        @NotBlank @Pattern(regexp = "\\d{5}-?\\d{3}", message = "deve ter 8 dígitos (00000-000)") String cep,
        @NotBlank @Size(max = 100) String cidade,
        @NotBlank @Size(max = 100) String bairro,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "deve ser a sigla da UF") String estado,
        @NotNull TipoDeResidencia tipoDeResidencia) {
}
