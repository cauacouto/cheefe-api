package com.couto.chefe_api.mapper;

import com.couto.chefe_api.dto.ChefeRequestDto;
import com.couto.chefe_api.dto.ChefeResponseDto;
import com.couto.chefe_api.dto.RegisterRequestDto;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Reputacao;
import org.mapstruct.*;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Mapper(config = MapeamentoEstrito.class, imports = Reputacao.class)
public interface ChefeMapper {


    // o menor preço dos menus ativos vem de MenuRepository.menoresPrecos, preenchido pelo ChefeService
    @Mapping(target = "precoAPartirDe", ignore = true)
    @Mapping(target = "notaMedia", expression = "java(Reputacao.media(model.getSomaNotas(), model.getTotalAvaliacoes()))")
    ChefeResponseDto toDto(ChefeModel model);


    // e-mail vem do token de registro e a foto do upload/Google (LoginService); o resto nasce com os defaults
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "agendamentos", ignore = true)
    @Mapping(target = "pratos", ignore = true)
    @Mapping(target = "disponivel", ignore = true)
    @Mapping(target = "ativo", ignore = true)
    @Mapping(target = "suspenso", ignore = true)
    @Mapping(target = "bloqueadoEm", ignore = true)
    @Mapping(target = "bloqueioMotivo", ignore = true)
    @Mapping(target = "somaNotas", ignore = true)
    @Mapping(target = "totalAvaliacoes", ignore = true)
    // telefone verificado só pelo fluxo de SMS (TelefoneService), nunca pelo corpo
    @Mapping(target = "telefoneVerificado", ignore = true)
    @Mapping(target = "telefoneVerificadoEm", ignore = true)
    // a cidade de origem entra depois, no PATCH /chefe/me (onboarding mínimo)
    @Mapping(target = "cidade", ignore = true)
    @Mapping(target = "estado", ignore = true)
    ChefeModel toModel(RegisterRequestDto dto);

@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    // PATCH /chefe/me: só os campos do perfil; conta, moderação, foto e relações nunca vêm do corpo
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "agendamentos", ignore = true)
    @Mapping(target = "pratos", ignore = true)
    @Mapping(target = "disponivel", ignore = true)
    @Mapping(target = "ativo", ignore = true)
    @Mapping(target = "suspenso", ignore = true)
    @Mapping(target = "bloqueadoEm", ignore = true)
    @Mapping(target = "bloqueioMotivo", ignore = true)
    @Mapping(target = "somaNotas", ignore = true)
    @Mapping(target = "totalAvaliacoes", ignore = true)
    // telefone verificado só pelo fluxo de SMS (TelefoneService), nunca pelo corpo
    @Mapping(target = "telefoneVerificado", ignore = true)
    @Mapping(target = "telefoneVerificadoEm", ignore = true)
    void updateModelFromDto(ChefeRequestDto dto, @MappingTarget ChefeModel model);



}
