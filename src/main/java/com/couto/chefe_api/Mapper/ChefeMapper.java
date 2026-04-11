package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.ChefeRequestDto;
import com.couto.chefe_api.Dtos.ChefeResponseDto;
import com.couto.chefe_api.domin.ChefeModel;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface ChefeMapper {


    ChefeResponseDto toDto(ChefeModel model);

    ChefeModel toModel(ChefeRequestDto dto);

@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
void updateModelFromDto(ChefeRequestDto dto , @MappingTarget ChefeModel model);
}
