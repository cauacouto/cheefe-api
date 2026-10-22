package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.ChefeRequestDto;
import com.couto.chefe_api.Dtos.ChefeResponseDto;
import com.couto.chefe_api.Dtos.RegisterRequestDto;
import com.couto.chefe_api.domin.ChefeModel;
import org.mapstruct.*;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Mapper(config = MapeamentoEstrito.class)
public interface ChefeMapper {


    ChefeResponseDto toDto(ChefeModel model);


    // e-mail vem do token de registro e a foto do upload/Google (LoginService); o resto nasce com os defaults
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "agendamentos", ignore = true)
    @Mapping(target = "pratos", ignore = true)
    @Mapping(target = "disponivel", ignore = true)
    @Mapping(target = "ativo", ignore = true)
    @Mapping(target = "suspenso", ignore = true)
    ChefeModel toModel(RegisterRequestDto dto);

@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    // PATCH /chefe/me: só os campos do perfil; conta, moderação, foto e relações nunca vêm do corpo
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "agendamentos", ignore = true)
    @Mapping(target = "pratos", ignore = true)
    @Mapping(target = "disponivel", ignore = true)
    @Mapping(target = "ativo", ignore = true)
    @Mapping(target = "suspenso", ignore = true)
    void updateModelFromDto(ChefeRequestDto dto, @MappingTarget ChefeModel model);



}
