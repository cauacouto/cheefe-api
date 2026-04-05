package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.ChefeRequestDto;
import com.couto.chefe_api.Dtos.ChefeResponseDto;
import com.couto.chefe_api.domin.ChefeModel;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ChefeMapper {


    ChefeResponseDto toDto(ChefeModel model);

    ChefeModel toModel(ChefeRequestDto dto);


    void updateModelFromDto(ChefeRequestDto dto , @MappingTarget ChefeModel model);
}
