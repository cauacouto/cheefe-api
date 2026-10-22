package com.couto.chefe_api.domain;

import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.hibernate.annotations.BatchSize;
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
import java.util.List;
import java.util.Objects;

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

    // listas paginadas: os chefs de todos os agendamentos da página vêm numa query só
    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(
            name = "agendamento_chefe",
            joinColumns = @JoinColumn(name = "agendamento_id"),
            inverseJoinColumns = @JoinColumn(name = "chefe_id")
    )
    private List<ChefeModel> chefes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endereco_id")
    private EnderecoModel endereco;

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

    /** O chef sai do pedido; devolve se ainda restam chefs (senão o pedido vira RECUSADO). */
    public boolean recusar(ChefeModel chefe, LocalDateTime agora) {
        exigirStatus(StatusAgendamento.PENDENTE, "recusar");
        exigirAntesDoInicio(agora, "recusar");
        return retirar(chefe, StatusAgendamento.RECUSADO);
    }

    /** O chef sai do agendamento confirmado; devolve se ainda restam chefs (senão ele vira CANCELADO). */
    public boolean desistir(ChefeModel chefe, LocalDateTime agora) {
        exigirStatus(StatusAgendamento.CONFIRMADO, "desistir de");
        exigirAntesDoInicio(agora, "desistir de");
        return retirar(chefe, StatusAgendamento.CANCELADO);
    }

    public void concluirPeloChef(LocalDateTime agora) {
        exigirStatus(StatusAgendamento.CONFIRMADO, "concluir");
        if (dataHoraInicial == null || agora.isBefore(dataHoraInicial)) {
            throw new AgendamentoTransicaoInvalidaException("Só é possível concluir depois do início do agendamento");
        }
        status = StatusAgendamento.CONCLUIDO;
    }

    /** Rotina: conclui o confirmado cujo fim já passou; false (sem erro) quando não se aplica. */
    public boolean concluirPeloSistema(LocalDateTime agora) {
        if (status != StatusAgendamento.CONFIRMADO || dataHoraFinal == null || agora.isBefore(dataHoraFinal)) {
            return false;
        }
        status = StatusAgendamento.CONCLUIDO;
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

    /** Soma do valor/hora dos chefs vezes a duração, já em reais com 2 casas (o mesmo valor que vai para o banco). */
    public static BigDecimal calcularValor(List<ChefeModel> chefes, LocalDateTime inicio, LocalDateTime fim) {
        BigDecimal horas = BigDecimal.valueOf(ChronoUnit.MINUTES.between(inicio, fim))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        return chefes.stream()
                .map(ChefeModel::getValorHora)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(horas)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean retirar(ChefeModel chefe, StatusAgendamento seNaoSobrarNinguem) {
        chefes.removeIf(c -> c.getId().equals(chefe.getId()));
        if (chefes.isEmpty()) {
            status = seNaoSobrarNinguem;
            return false;
        }
        if (dataHoraInicial != null && dataHoraFinal != null) {
            valorTotal = calcularValor(chefes, dataHoraInicial, dataHoraFinal);
        }
        return true;
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
