package com.couto.chefe_api.Dtos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AbrirConversaDto(@NotNull UUID chefeId) {
}
