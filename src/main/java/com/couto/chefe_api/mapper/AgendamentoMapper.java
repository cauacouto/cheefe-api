package com.couto.chefe_api.mapper;

import com.couto.chefe_api.dto.AgendamentoResponseDto;
import com.couto.chefe_api.dto.ListaAgendamentoChefeDto;
import com.couto.chefe_api.dto.ListaAgendamentoUsuarioDto;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.Reputacao;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import java.util.List;

@Mapper(config = MapeamentoEstrito.class, imports = Reputacao.class)
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
    @Mapping(source = "dataHoraInicial",             target = "dataHora")
    @Mapping(target = "clienteNotaMedia", expression = "java(agendamento.getUsuario() == null ? null : "
            + "Reputacao.media(agendamento.getUsuario().getSomaNotas(), agendamento.getUsuario().getTotalAvaliacoes()))")
    @Mapping(source = "usuario.totalAvaliacoes",     target = "clienteTotalAvaliacoes")
    ListaAgendamentoChefeDto toChefeDto(Agendamento agendamento);

    @IterableMapping(qualifiedByName = "toChefeDto")
    List<ListaAgendamentoChefeDto> toListChefe(List<Agendamento> agendamentos);

    // ─── Usuario ──────────────────────────────────────────────────────
    @Named("toUsuarioDto")
    @Mapping(source = "dataHoraInicial", target = "dataHora")
    ListaAgendamentoUsuarioDto toUsuarioDto(Agendamento agendamento);

    @IterableMapping(qualifiedByName = "toUsuarioDto")
    List<ListaAgendamentoUsuarioDto> toListUser(List<Agendamento> agendamentos);
}
