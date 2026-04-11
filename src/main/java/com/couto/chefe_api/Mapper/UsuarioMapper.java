package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.EnderecoRequestDto;
import com.couto.chefe_api.Dtos.UserRequestDto;
import com.couto.chefe_api.Dtos.UserResponseDto;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.domin.EnderecoModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {


    UserResponseDto toDto(UserModel model);

    @Mapping(target = "endereco",ignore = true)
    UserModel toModel(UserRequestDto dto);

    EnderecoModel toModel(EnderecoRequestDto dto);


}
