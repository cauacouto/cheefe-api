package com.couto.chefe_api.Dtos;

import com.couto.chefe_api.enums.StatusDenuncia;

public record DenunciaCriadaDto(Long id, StatusDenuncia status) {
}
