package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.ChefeRequestDto;
import com.couto.chefe_api.Dtos.ChefeResponseDto;
import com.couto.chefe_api.Dtos.RegisterRequestDto;
import com.couto.chefe_api.domin.ChefeModel;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ChefeMapper {


    ChefeResponseDto toDto(ChefeModel model);

    @Mapping(target = "password",source = "senhaCriptografada")
    ChefeModel toModel(RegisterRequestDto dto,String senhaCriptografada);

@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
void updateModelFromDto(ChefeRequestDto dto , @MappingTarget ChefeModel model);
}
