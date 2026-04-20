package com.couto.chefe_api.Mapper;

import com.couto.chefe_api.Dtos.AgendamentoResponseDto;
import com.couto.chefe_api.Dtos.ListaAgendamentoChefeDto;
import com.couto.chefe_api.Dtos.ListaAgendamentoUsuarioDto;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.enums.Especialidades;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import java.util.List;

@Mapper(componentModel = "spring")
public interface AgendamentoMapper {

    // ─── toDto (AgendamentoResponseDto) ───────────────────────────────
    @Mapping(source = "usuario.nome",              target = "nomeUsuario")
    @Mapping(source = "endereco.rua",              target = "rua")
    @Mapping(source = "endereco.numero",           target = "numero")
    @Mapping(source = "endereco.bairro",           target = "bairro")
    @Mapping(source = "endereco.cidade",           target = "cidade")
    @Mapping(source = "endereco.estado",           target = "estado")
    @Mapping(source = "endereco.cep",              target = "cep")
    @Mapping(source = "endereco.complemento",      target = "complemento")
    @Mapping(source = "endereco.tipoDeResidencia", target = "tipoDeResidencia")
    @Mapping(source = "chefes", target = "chefes", qualifiedByName = "chefsParaNomes")
    AgendamentoResponseDto toDto(Agendamento agendamento);

    // ─── Chefe ────────────────────────────────────────────────────────
    @Named("toChefeDto")
    @Mapping(source = "usuario.nome",              target = "nomeUsuario")
    @Mapping(source = "endereco.rua",              target = "rua")
    @Mapping(source = "endereco.numero",           target = "numero")
    @Mapping(source = "endereco.bairro",           target = "bairro")
    @Mapping(source = "endereco.cidade",           target = "cidade")
    @Mapping(source = "endereco.tipoDeResidencia", target = "tipoDeResidencia")
    @Mapping(source = "endereco.complemento",      target = "complemento")
    @Mapping(source = "endereco.estado",           target = "estado")
    @Mapping(source = "endereco.cep",              target = "cep")
    @Mapping(source = "quantidadePessoas",           target = "quantidadePessoas")
    ListaAgendamentoChefeDto toChefeDto(Agendamento agendamento);

    @IterableMapping(qualifiedByName = "toChefeDto")
    List<ListaAgendamentoChefeDto> toListChefe(List<Agendamento> agendamentos);

    // ─── Usuario ──────────────────────────────────────────────────────
    @Named("toUsuarioDto")
    @Mapping(source = "chefes", target = "nomesChefes",    qualifiedByName = "mapNomeChefe")
    @Mapping(source = "chefes", target = "especialidades", qualifiedByName = "mapEspecialidade")
    ListaAgendamentoUsuarioDto toUsuarioDto(Agendamento agendamento);

    @Mapping(source = "criadoEm",   target = "criadoEm")
    @IterableMapping(qualifiedByName = "toUsuarioDto")
    List<ListaAgendamentoUsuarioDto> toListUser(List<Agendamento> agendamentos);

    // ─── Helpers ──────────────────────────────────────────────────────
    @Named("chefsParaNomes")
    default List<String> chefsParaNomes(List<ChefeModel> chefs) {
        if (chefs == null) return List.of();
        return chefs.stream().map(ChefeModel::getNome).toList();
    }

    @Named("mapNomeChefe")
    default String mapNomeChefe(List<ChefeModel> chefes) {
        if (chefes == null || chefes.isEmpty()) return null;
        return chefes.get(0).getNome();
    }

    @Named("mapEspecialidade")
    default Especialidades mapEspecialidade(List<ChefeModel> chefes) {
        if (chefes == null || chefes.isEmpty()) return null;
        return chefes.get(0).getEspecialidade();
    }
}