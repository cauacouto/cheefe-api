package com.couto.chefe_api.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Todo campo de destino precisa estar mapeado ou ignorado explicitamente: um nome diferente
 * (ex.: imagemUrl x imageUrl) vira erro de compilação em vez de um campo sempre nulo, e um
 * update nunca toca num campo que ninguém decidiu expor (id, e-mail, ativo...).
 */
@MapperConfig(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MapeamentoEstrito {
}
