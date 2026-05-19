package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.*;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.domin.EnderecoModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {


    UserResponseDto toDto(UserModel model);

    @Mapping(target = "endereco",ignore = true)
    @Mapping(target = "password",source = "senhaCriptografada")
    UserModel toModel(RegisterRequestDto dto, String senhaCriptografada);

    EnderecoModel toModel(EnderecoRequestDto dto);

    EnderecoResponseDto toDto(EnderecoModel model);


}
