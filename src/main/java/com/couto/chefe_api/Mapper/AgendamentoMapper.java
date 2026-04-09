package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.AgendamentoResponseDto;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel="spring")
public interface AgendamentoMapper {

    @Mapping(source = "usuario.nome",          target = "nomeUsuario")
    @Mapping(source = "endereco.rua",          target = "rua")
    @Mapping(source = "endereco.numero",       target = "numero")
    @Mapping(source = "endereco.bairro",       target = "bairro")
    @Mapping(source = "endereco.cidade",       target = "cidade")
    @Mapping(source = "endereco.estado",       target = "estado")
    @Mapping(source = "endereco.cep",          target = "cep")
    @Mapping(source = "endereco.complemento",  target = "complemento")
    @Mapping(source = "endereco.tipoDeResidencia", target = "tipoDeResidencia")
    @Mapping(source = "chefes", target = "chefes", qualifiedByName = "chefsParaNomes")
    AgendamentoResponseDto toDto(Agendamento agendamento);


    List<AgendamentoResponseDto> toDtoList(List<Agendamento> agendamentos);

    @Named("chefsParaNomes")
    default List<String> chefsParaNomes(List<ChefeModel> chefs) {
        if (chefs == null) return List.of();
        return chefs.stream().map(ChefeModel::getNome).toList();
    }
}
