package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.CadastraPratosDtoRequest;
import com.couto.chefe_api.Dtos.CadastratPratosDtoResponse;
import com.couto.chefe_api.Dtos.ListarPratosChefes;
import com.couto.chefe_api.domin.Pratos;
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
    CadastratPratosDtoResponse toDto(Pratos model);

    @Mapping(source = "chefeModel.id", target = "chefeId")
    @Mapping(source = "chefeModel.nome", target = "nomeChefe")
    ListarPratosChefes toDtoLista(Pratos model);



    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "chefeModel", ignore = true)
    void updatePrato(CadastraPratosDtoRequest dto , @MappingTarget Pratos pratos);
}
