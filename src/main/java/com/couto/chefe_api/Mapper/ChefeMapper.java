package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.ChefeRequestDto;
import com.couto.chefe_api.Dtos.ChefeResponseDto;
import com.couto.chefe_api.Dtos.RegisterRequestDto;
import com.couto.chefe_api.domin.ChefeModel;
import org.mapstruct.*;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Mapper(componentModel = "spring")
public interface ChefeMapper {


    ChefeResponseDto toDto(ChefeModel model);


    @Mapping(target = "email", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    ChefeModel toModel(RegisterRequestDto dto);

@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
void updateModelFromDto(ChefeRequestDto dto , @MappingTarget ChefeModel model);



}
