package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.StatusDenuncia;

public record DenunciaCriadaDto(Long id, StatusDenuncia status) {
}
