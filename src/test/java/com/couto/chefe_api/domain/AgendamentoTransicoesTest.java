package com.couto.chefe_api.domain;

import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.exception.AgendamentoTransicaoInvalidaException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** As regras de status e horário de cada transição, sem banco (spec: tabela de transições). */
class AgendamentoTransicoesTest {

    static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 20, 19, 0);
    static final LocalDateTime FIM = INICIO.plusHours(2);

    final ChefeModel bia = chefe("Bia", "100");
    final ChefeModel caio = chefe("Caio", "50");

    @Test
    void cancelarConfirmadoComExatamente24hPode() {
        Agendamento a = agendamento(StatusAgendamento.CONFIRMADO, bia);
        a.cancelarPeloCliente(INICIO.minusHours(24));
        assertThat(a.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
    }

    @Test
    void cancelarConfirmadoCom23h59Da409() {
        Agendamento a = agendamento(StatusAgendamento.CONFIRMADO, bia);
        assertThatThrownBy(() -> a.cancelarPeloCliente(INICIO.minusHours(23).minusMinutes(59)))
                .isInstanceOf(AgendamentoTransicaoInvalidaException.class);
        assertThat(a.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
    }

    @Test
    void cancelarPendenteAteOInicioMasNaoDepois() {
        Agendamento a = agendamento(StatusAgendamento.PENDENTE, bia);
        a.cancelarPeloCliente(INICIO.minusMinutes(1));
        assertThat(a.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);

        Agendamento b = agendamento(StatusAgendamento.PENDENTE, bia);
        assertThatThrownBy(() -> b.cancelarPeloCliente(INICIO)).isInstanceOf(AgendamentoTransicaoInvalidaException.class);
    }

    @Test
    void depoisDoInicioPendenteNaoEhConfirmadoRecusadoNemEditado() {
        assertThatThrownBy(() -> agendamento(StatusAgendamento.PENDENTE, bia).confirmar(INICIO))
                .isInstanceOf(AgendamentoTransicaoInvalidaException.class);
        assertThatThrownBy(() -> agendamento(StatusAgendamento.PENDENTE, bia).recusar(bia, INICIO.plusMinutes(1)))
                .isInstanceOf(AgendamentoTransicaoInvalidaException.class);
        assertThatThrownBy(() -> agendamento(StatusAgendamento.PENDENTE, bia).exigirEditavel(INICIO))
                .isInstanceOf(AgendamentoTransicaoInvalidaException.class);

        Agendamento a = agendamento(StatusAgendamento.PENDENTE, bia);
        a.confirmar(INICIO.minusMinutes(1));
        assertThat(a.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
    }

    @Test
    void chefConcluiSoDepoisDoInicio() {
        Agendamento a = agendamento(StatusAgendamento.CONFIRMADO, bia);
        assertThatThrownBy(() -> a.concluirPeloChef(INICIO.minusMinutes(1)))
                .isInstanceOf(AgendamentoTransicaoInvalidaException.class);

        a.concluirPeloChef(INICIO);
        assertThat(a.getStatus()).isEqualTo(StatusAgendamento.CONCLUIDO);
        assertThat(a.getConcluidoEm()).isEqualTo(INICIO);
    }

    @Test
    void recusaParcialTiraOChefERecalculaEMantemPendente() {
        Agendamento a = agendamento(StatusAgendamento.PENDENTE, bia, caio);

        boolean restam = a.recusar(caio, INICIO.minusDays(1));

        assertThat(restam).isTrue();
        assertThat(a.getStatus()).isEqualTo(StatusAgendamento.PENDENTE);
        assertThat(a.getChefes()).containsExactly(bia);
        assertThat(a.getValorTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    void recusaDoUltimoChefViraRecusado() {
        Agendamento a = agendamento(StatusAgendamento.PENDENTE, bia);

        assertThat(a.recusar(bia, INICIO.minusDays(1))).isFalse();
        assertThat(a.getStatus()).isEqualTo(StatusAgendamento.RECUSADO);
        assertThat(a.getChefes()).isEmpty();
    }

    @Test
    void desistenciaParcialMantemConfirmadoEDoUltimoCancela() {
        Agendamento a = agendamento(StatusAgendamento.CONFIRMADO, bia, caio);

        assertThat(a.desistir(bia, INICIO.minusHours(1))).isTrue();
        assertThat(a.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
        assertThat(a.getValorTotal()).isEqualByComparingTo("100.00");

        assertThat(a.desistir(caio, INICIO.minusHours(1))).isFalse();
        assertThat(a.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
    }

    @Test
    void desistirSoDeConfirmadoERecusarSoDePendente() {
        assertThatThrownBy(() -> agendamento(StatusAgendamento.PENDENTE, bia).desistir(bia, INICIO.minusDays(1)))
                .isInstanceOf(AgendamentoTransicaoInvalidaException.class);
        assertThatThrownBy(() -> agendamento(StatusAgendamento.CONFIRMADO, bia).recusar(bia, INICIO.minusDays(1)))
                .isInstanceOf(AgendamentoTransicaoInvalidaException.class);
        assertThatThrownBy(() -> agendamento(StatusAgendamento.CONFIRMADO, bia).desistir(bia, INICIO))
                .isInstanceOf(AgendamentoTransicaoInvalidaException.class);
    }

    @Test
    void estadoFinalNaoMudaMais() {
        LocalDateTime antes = INICIO.minusDays(2);
        List<Consumer<Agendamento>> acoes = List.of(
                a -> a.confirmar(antes), a -> a.exigirEditavel(antes), a -> a.cancelarPeloCliente(antes),
                a -> a.recusar(bia, antes), a -> a.desistir(bia, antes), a -> a.concluirPeloChef(INICIO.plusHours(1)));
        for (StatusAgendamento finalStatus : List.of(StatusAgendamento.CANCELADO, StatusAgendamento.RECUSADO,
                StatusAgendamento.CONCLUIDO, StatusAgendamento.EXPIRADO)) {
            for (Consumer<Agendamento> acao : acoes) {
                Agendamento a = agendamento(finalStatus, bia);
                assertThatThrownBy(() -> acao.accept(a))
                        .isInstanceOf(AgendamentoTransicaoInvalidaException.class)
                        .hasMessageContaining(finalStatus.name());
                assertThat(a.getStatus()).isEqualTo(finalStatus);
            }
        }
    }

    @Test
    void sistemaConcluiSoDepoisDoFimEExpiraSoDepoisDoInicio() {
        Agendamento confirmado = agendamento(StatusAgendamento.CONFIRMADO, bia);
        assertThat(confirmado.concluirPeloSistema(FIM.minusMinutes(1))).isFalse();
        assertThat(confirmado.getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
        assertThat(confirmado.getConcluidoEm()).isNull();
        assertThat(confirmado.concluirPeloSistema(FIM)).isTrue();
        assertThat(confirmado.getStatus()).isEqualTo(StatusAgendamento.CONCLUIDO);
        assertThat(confirmado.getConcluidoEm()).isEqualTo(FIM);
        assertThat(confirmado.concluirPeloSistema(FIM.plusHours(1))).isFalse();
        assertThat(confirmado.getConcluidoEm()).isEqualTo(FIM);

        Agendamento pendente = agendamento(StatusAgendamento.PENDENTE, bia);
        assertThat(pendente.expirarPeloSistema(INICIO.minusMinutes(1))).isFalse();
        assertThat(pendente.expirarPeloSistema(INICIO)).isTrue();
        assertThat(pendente.getStatus()).isEqualTo(StatusAgendamento.EXPIRADO);
        assertThat(agendamento(StatusAgendamento.CONFIRMADO, bia).expirarPeloSistema(INICIO.plusDays(1))).isFalse();
    }

    @Test
    void semDatasCancelaERecusaMasNaoConclui() {
        Agendamento cancelavel = semDatas(StatusAgendamento.PENDENTE);
        cancelavel.cancelarPeloCliente(INICIO);
        assertThat(cancelavel.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);

        Agendamento recusavel = semDatas(StatusAgendamento.PENDENTE);
        assertThat(recusavel.recusar(bia, INICIO)).isFalse();

        Agendamento confirmado = semDatas(StatusAgendamento.CONFIRMADO);
        assertThatThrownBy(() -> confirmado.concluirPeloChef(INICIO)).isInstanceOf(AgendamentoTransicaoInvalidaException.class);
        assertThat(confirmado.concluirPeloSistema(INICIO)).isFalse();
        assertThat(semDatas(StatusAgendamento.PENDENTE).expirarPeloSistema(INICIO)).isFalse();
    }

    @Test
    void valorJaSaiComDuasCasasArredondadoParaCima() {
        // 100,25 x 2,5h = 250,625: o mesmo valor na notificação e no banco (numeric(10,2))
        BigDecimal valor = Agendamento.calcularValor(List.of(chefe("Bia", "100.25")), INICIO, INICIO.plusMinutes(150));

        assertThat(valor).isEqualTo(new BigDecimal("250.63"));
    }

    // ---------------------------------------------------------------------

    Agendamento agendamento(StatusAgendamento status, ChefeModel... chefes) {
        Agendamento a = new Agendamento();
        a.setStatus(status);
        a.setChefes(new ArrayList<>(List.of(chefes)));
        a.setDataHoraInicial(INICIO);
        a.setDataHoraFinal(FIM);
        a.setValorTotal(Agendamento.calcularValor(a.getChefes(), INICIO, FIM));
        return a;
    }

    Agendamento semDatas(StatusAgendamento status) {
        Agendamento a = new Agendamento();
        a.setStatus(status);
        a.setChefes(new ArrayList<>(List.of(bia)));
        return a;
    }

    static ChefeModel chefe(String nome, String valorHora) {
        ChefeModel chefe = new ChefeModel();
        chefe.setId(UUID.randomUUID());
        chefe.setNome(nome);
        chefe.setValorHora(new BigDecimal(valorHora));
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        return chefe;
    }
}
