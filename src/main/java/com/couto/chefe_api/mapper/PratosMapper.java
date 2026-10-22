package com.couto.chefe_api.mapper;

import com.couto.chefe_api.dto.CadastraPratosDtoRequest;
import com.couto.chefe_api.dto.CadastraPratosDtoResponse;
import com.couto.chefe_api.dto.ListarPratosChefes;
import com.couto.chefe_api.domain.Pratos;
import org.mapstruct.*;

@Mapper(config = MapeamentoEstrito.class)
public interface PratosMapper {

    // a foto só entra pelo upload validado e o dono vem do token
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "chefeModel", ignore = true)
    Pratos toModel(CadastraPratosDtoRequest dto);

    @Mapping(source = "chefeModel.nome", target = "nomeChefe")
    @Mapping(source = "nomePrato", target = "nomePrato") // mesmo nome agora
    CadastraPratosDtoResponse toDto(Pratos model);

    @Mapping(source = "chefeModel.id", target = "chefeId")
    @Mapping(source = "chefeModel.nome", target = "nomeChefe")
    ListarPratosChefes toDtoLista(Pratos model);



    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "chefeModel", ignore = true)
    void updatePrato(CadastraPratosDtoRequest dto , @MappingTarget Pratos pratos);
}
