package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.*;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.domin.EnderecoModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapeamentoEstrito.class)
public interface UsuarioMapper {


    UserResponseDto toDto(UserModel model);

    @Mapping(target = "endereco",ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "agendamento", ignore = true)
    // onboarding mínimo: o telefone entra depois, no PUT /usuario/me
    @Mapping(target = "telefone", ignore = true)
    UserModel toEntity(RegisterRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    EnderecoModel toModel(EnderecoRequestDto dto);

    EnderecoResponseDto toDto(EnderecoModel model);


}
