package com.couto.chefe_api.mapper;

import com.couto.chefe_api.dto.AgendamentoResponseDto;
import com.couto.chefe_api.dto.ListaAgendamentoChefeDto;
import com.couto.chefe_api.dto.ListaAgendamentoUsuarioDto;
import com.couto.chefe_api.dto.MenuDoAgendamentoDto;
import com.couto.chefe_api.dto.RestricaoDto;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.Reputacao;
import com.couto.chefe_api.domain.RestricaoAgendamento;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

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
    @Mapping(source = ".",                         target = "menu", qualifiedByName = "menuDoAgendamento")
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
    @Mapping(source = ".",                           target = "menu", qualifiedByName = "menuDoAgendamento")
    ListaAgendamentoChefeDto toChefeDto(Agendamento agendamento);

    @IterableMapping(qualifiedByName = "toChefeDto")
    List<ListaAgendamentoChefeDto> toListChefe(List<Agendamento> agendamentos);

    // ─── Usuario ──────────────────────────────────────────────────────
    @Named("toUsuarioDto")
    @Mapping(source = "dataHoraInicial", target = "dataHora")
    @Mapping(source = ".",               target = "menu", qualifiedByName = "menuDoAgendamento")
    ListaAgendamentoUsuarioDto toUsuarioDto(Agendamento agendamento);

    @IterableMapping(qualifiedByName = "toUsuarioDto")
    List<ListaAgendamentoUsuarioDto> toListUser(List<Agendamento> agendamentos);

    // ─── Menu e restrições ────────────────────────────────────────────
    /**
     * Do que está gravado no agendamento (a cópia), nunca do menu atual. O id sai da referência sem carregá-la:
     * ler o id de um proxy não inicializa, então a listagem não faz uma consulta por linha. Nulo nos agendamentos
     * de antes dos menus.
     */
    @Named("menuDoAgendamento")
    default MenuDoAgendamentoDto menuDoAgendamento(Agendamento agendamento) {
        if (agendamento.getMenuNome() == null) {
            return null;
        }
        Long menuId = agendamento.getMenu() == null ? null : agendamento.getMenu().getId();
        return new MenuDoAgendamentoDto(menuId, agendamento.getMenuNome(), agendamento.getPrecoPorPessoa(),
                agendamento.getDuracaoMinutos(), agendamento.getIngredientesInclusos());
    }

    /** Na ordem do enum, para a resposta ser estável. */
    default List<RestricaoDto> restricoes(Set<RestricaoAgendamento> restricoes) {
        return restricoes.stream().sorted(Comparator.comparing(RestricaoAgendamento::tipo))
                .map(r -> new RestricaoDto(r.tipo(), r.quantidade())).toList();
    }
}
