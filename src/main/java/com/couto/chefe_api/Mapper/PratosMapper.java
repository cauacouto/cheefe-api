package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.CadastraPratosDtoRequest;
import com.couto.chefe_api.Dtos.CadastratPratosDtoResponse;
import com.couto.chefe_api.Dtos.ListarPratosChefes;
import com.couto.chefe_api.domin.Pratos;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface PratosMapper {

    @Mapping(source = "nomePrato", target = "nomePrato")
    Pratos toModel(CadastraPratosDtoRequest dto);

    @Mapping(source = "chefeModel.nome", target = "nomeChefe")
    @Mapping(source = "nomePrato", target = "nomePrato") // mesmo nome agora
    CadastratPratosDtoResponse toDto(Pratos model);

    @Mapping(source = "chefeModel.nome", target = "nomeChefe")
    ListarPratosChefes toDtoLista(Pratos model);



    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updatePrato(CadastraPratosDtoRequest dto , @MappingTarget Pratos pratos);
}
