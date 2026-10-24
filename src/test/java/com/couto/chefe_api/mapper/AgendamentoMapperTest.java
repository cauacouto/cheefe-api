package com.couto.chefe_api.mapper;

import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.dto.ListaAgendamentoChefeDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AgendamentoMapperTest {

    private final AgendamentoMapper mapper = new AgendamentoMapperImpl();

    @Test
    void agendamentoSemClienteNaoQuebraAListaDoChef() {
        // o cliente pode ter apagado a conta: o agendamento fica sem usuário
        ListaAgendamentoChefeDto dto = mapper.toChefeDto(new Agendamento());

        assertThat(dto.getClienteNotaMedia()).isNull();
        assertThat(dto.getClienteTotalAvaliacoes()).isZero();
        assertThat(dto.getNomeUsuario()).isNull();
    }

    @Test
    void clienteComCincoAvaliacoesMostraAMedia() {
        UserModel cliente = new UserModel();
        cliente.setSomaNotas(23);
        cliente.setTotalAvaliacoes(5);
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);

        ListaAgendamentoChefeDto dto = mapper.toChefeDto(agendamento);

        assertThat(dto.getClienteNotaMedia()).isEqualByComparingTo(new BigDecimal("4.6"));
        assertThat(dto.getClienteTotalAvaliacoes()).isEqualTo(5);
    }
}
