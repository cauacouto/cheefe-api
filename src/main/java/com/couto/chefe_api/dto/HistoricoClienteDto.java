package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.OcasiaoMenu;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoRestricao;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** O que o chef tem com um cliente: reputação, restrições que se repetem, a nota privada e os últimos agendamentos. */
public record HistoricoClienteDto(
        Cliente cliente,
        ReputacaoDto reputacao,
        List<RestricaoFrequente> restricoesFrequentes,
        NotaClienteDto nota,
        List<Item> agendamentos
) {

    public record Cliente(UUID id, String primeiroNome, String foto) {
    }

    /** Em quantos agendamentos {@code CONFIRMADO} ou {@code CONCLUIDO} o tipo apareceu. */
    public record RestricaoFrequente(TipoRestricao tipo, long agendamentos) {
    }

    public record Item(
            Long id,
            @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime dataHoraInicial,
            StatusAgendamento status,
            MenuDoAgendamentoDto menu,
            Integer quantidadePessoas,
            OcasiaoMenu ocasiao,
            List<RestricaoDto> restricoes
    ) {
    }
}
