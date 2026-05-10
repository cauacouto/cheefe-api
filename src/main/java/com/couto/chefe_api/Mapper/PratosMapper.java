package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.CadastraPratosDtoRequest;
import com.couto.chefe_api.Dtos.CadastratPratosDtoResponse;
import com.couto.chefe_api.domin.Pratos;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PratosMapper {


    CadastratPratosDtoResponse toDto (Pratos model);

    Pratos toModel (CadastraPratosDtoRequest dto);

}
