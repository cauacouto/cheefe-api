package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.AgendamentoResquestDto;
import com.couto.chefe_api.Dtos.AgendendamenResponseDto;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel="spring")
public interface AgendamentoMapper {

    @Mapping(source = "user.nome",     target = "nomeUsuario")
    @Mapping(source = "endereco.rua",    target = "rua")
    @Mapping(source = "endereco.numero", target = "numero")
    @Mapping(source = "endereco.bairro", target = "bairro")
    @Mapping(source = "endereco.cidade", target = "cidade")
    @Mapping(source = "endereco.cep",    target = "cep")
    @Mapping(source = "endereco.complemento",      target = "complemento")
    @Mapping(source = "endereco.tipoDeResidencia", target = "tipoDeResidencia")
    @Mapping(source = "chefs", target = "chefs", qualifiedByName = "chefsParaNomes")

    Agendamento toModel(AgendamentoResquestDto dto);

    AgendendamenResponseDto toDto(Agendamento agendamento);


    List<AgendendamenResponseDto> toDtoList(List<Agendamento> agendamentos);

    @Named("chefsParaNomes")
    default List<String> chefsParaNomes(List<ChefeModel> chefs) {
        if (chefs == null) return List.of();
        return chefs.stream().map(ChefeModel::getNome).toList();
    }
}
