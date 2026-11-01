package com.couto.chefe_api.domain;

import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.couto.chefe_api.exception.AgendamentoTransicaoInvalidaException;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// só as colunas que mudaram vão no UPDATE: salvar o agendamento não sobrescreve o que outra transação gravou
@DynamicUpdate
@Entity()
@Table(name = "agendamentos")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Agendamento  implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)

    private Long id;

    @Column(name = "quantidade_pessoas")
    private Integer quantidadePessoas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserModel usuario;

    /** O chef continua no agendamento mesmo depois de recusar ou desistir. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chefe_id")
    private ChefeModel chefe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endereco_id")
    private EnderecoModel endereco;

    /** Quando passou a CONCLUIDO; começa o prazo de 7 dias das avaliações. */
    private LocalDateTime concluidoEm;

    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime dataHoraInicial;

    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime dataHoraFinal;

    @Column(precision = 10,scale = 2)
    private BigDecimal valorTotal;


    @Enumerated(EnumType.STRING)
    private StatusAgendamento status = StatusAgendamento.PENDENTE;



    @Column(updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    public void Prepersist(){
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }

    // ─── Transições (spec: tabela de transições). Só regra de status e horário, sem banco. ───

    /** Prazo mínimo para o cliente cancelar um agendamento já confirmado. */
    public static final Duration PRAZO_CANCELAMENTO_CONFIRMADO = Duration.ofHours(24);

    public void confirmar(LocalDateTime agora) {
        exigirStatus(StatusAgendamento.PENDENTE, "confirmar");
        exigirAntesDoInicio(agora, "confirmar");
        status = StatusAgendamento.CONFIRMADO;
    }

    public void exigirEditavel(LocalDateTime agora) {
        exigirStatus(StatusAgendamento.PENDENTE, "editar");
        exigirAntesDoInicio(agora, "editar");
    }

    /** Pendente: até o início. Confirmado: até 24h antes do início. */
    public void cancelarPeloCliente(LocalDateTime agora) {
        if (status == StatusAgendamento.PENDENTE) {
            exigirAntesDoInicio(agora, "cancelar");
        } else if (status == StatusAgendamento.CONFIRMADO) {
            if (dataHoraInicial != null && agora.isAfter(dataHoraInicial.minus(PRAZO_CANCELAMENTO_CONFIRMADO))) {
                throw new AgendamentoTransicaoInvalidaException(
                        "Agendamento confirmado só pode ser cancelado até 24 horas antes do início");
            }
        } else {
            throw naoPode("cancelar");
        }
        status = StatusAgendamento.CANCELADO;
    }

    public void recusar(LocalDateTime agora) {
        exigirStatus(StatusAgendamento.PENDENTE, "recusar");
        exigirAntesDoInicio(agora, "recusar");
        status = StatusAgendamento.RECUSADO;
    }

    public void desistir(LocalDateTime agora) {
        exigirStatus(StatusAgendamento.CONFIRMADO, "desistir de");
        exigirAntesDoInicio(agora, "desistir de");
        status = StatusAgendamento.CANCELADO;
    }

    public void concluirPeloChef(LocalDateTime agora) {
        exigirStatus(StatusAgendamento.CONFIRMADO, "concluir");
        if (dataHoraInicial == null || agora.isBefore(dataHoraInicial)) {
            throw new AgendamentoTransicaoInvalidaException("Só é possível concluir depois do início do agendamento");
        }
        status = StatusAgendamento.CONCLUIDO;
        concluidoEm = agora;
    }

    /** Rotina: conclui o confirmado cujo fim já passou; false (sem erro) quando não se aplica. */
    public boolean concluirPeloSistema(LocalDateTime agora) {
        if (status != StatusAgendamento.CONFIRMADO || dataHoraFinal == null || agora.isBefore(dataHoraFinal)) {
            return false;
        }
        status = StatusAgendamento.CONCLUIDO;
        concluidoEm = agora;
        return true;
    }

    /** Rotina: expira o pendente cujo início já passou; false (sem erro) quando não se aplica. */
    public boolean expirarPeloSistema(LocalDateTime agora) {
        if (status != StatusAgendamento.PENDENTE || dataHoraInicial == null || agora.isBefore(dataHoraInicial)) {
            return false;
        }
        status = StatusAgendamento.EXPIRADO;
        return true;
    }

    /** Valor/hora do chef vezes a duração, já em reais com 2 casas (o mesmo valor que vai para o banco). */
    public static BigDecimal calcularValor(ChefeModel chefe, LocalDateTime inicio, LocalDateTime fim) {
        BigDecimal horas = BigDecimal.valueOf(ChronoUnit.MINUTES.between(inicio, fim))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        BigDecimal valorHora = chefe.getValorHora() == null ? BigDecimal.ZERO : chefe.getValorHora();
        return valorHora.multiply(horas).setScale(2, RoundingMode.HALF_UP);
    }

    private void exigirStatus(StatusAgendamento esperado, String acao) {
        if (status != esperado) {
            throw naoPode(acao);
        }
    }

    /** Sem data (agendamentos antigos) não há horário para vencer. */
    private void exigirAntesDoInicio(LocalDateTime agora, String acao) {
        if (dataHoraInicial != null && !agora.isBefore(dataHoraInicial)) {
            throw new AgendamentoTransicaoInvalidaException("Não é possível " + acao + " um agendamento que já começou");
        }
    }

    private AgendamentoTransicaoInvalidaException naoPode(String acao) {
        return new AgendamentoTransicaoInvalidaException("Não é possível " + acao + " um agendamento " + status);
    }
}
