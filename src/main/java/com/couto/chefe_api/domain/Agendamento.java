package com.couto.chefe_api.domain;

import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.OcasiaoMenu;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
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
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;

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

    /** O menu pedido; nulo nos agendamentos de antes dos menus. */
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "menu_id")
    private Menu menu;

    // cópia do menu no momento do pedido: editar o menu depois não muda o que o cliente contratou
    @Column(length = 100)
    private String menuNome;

    @Column(precision = 10, scale = 2)
    private BigDecimal precoPorPessoa;

    private Integer duracaoMinutos;

    private Boolean ingredientesInclusos;

    @Enumerated(EnumType.STRING)
    @Column(length = 255)
    private OcasiaoMenu ocasiao;

    @ElementCollection
    @CollectionTable(name = "agendamento_restricao", joinColumns = @JoinColumn(name = "agendamento_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @BatchSize(size = 50) // as listagens carregam as restrições da página de uma vez
    private Set<RestricaoAgendamento> restricoes = new LinkedHashSet<>();

    @Column(length = 1000)
    private String observacoes;

    /** O que o cliente escreveu, quando o exibido em {@link #observacoes} foi mascarado. Nunca vai para a API. */
    @Column(length = 1000)
    private String observacoesOriginal;

    /** Quando o cliente confirmou ou ajustou os detalhes; nulo até ele agir. */
    private LocalDateTime detalhesConfirmadosEm;

    /** Marcas das rotinas: cada lembrete, resumo e aviso sai uma vez só. */
    private LocalDateTime lembreteClienteEm;
    private LocalDateTime resumoChefEm;
    private LocalDateTime avisoUmAnoEm;


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

    /** Antecedência mínima para o cliente confirmar ou ajustar os detalhes de um agendamento confirmado. */
    public static final Duration PRAZO_DETALHES = Duration.ofHours(48);

    /** Pedido recusado deixa de existir para o chef: ele não vê mais nada do cliente por aqui. */
    public boolean visivelPara(UUID chefeId) {
        return chefe.getId().equals(chefeId) && status != StatusAgendamento.RECUSADO;
    }

    /**
     * Endereço, restrições e observações do cliente só enquanto o serviço pode acontecer ou já aconteceu.
     * Cancelado ou expirado, o chef fica com o registro, sem os dados pessoais (LGPD: só o necessário).
     */
    public boolean detalhesDoClienteVisiveisAoChef() {
        return status == StatusAgendamento.PENDENTE || status == StatusAgendamento.CONFIRMADO
                || status == StatusAgendamento.CONCLUIDO;
    }

    public void confirmar(LocalDateTime agora) {
        exigirStatus(StatusAgendamento.PENDENTE, "confirmar");
        exigirAntesDoInicio(agora, "confirmar");
        status = StatusAgendamento.CONFIRMADO;
    }

    public void exigirEditavel(LocalDateTime agora) {
        exigirStatus(StatusAgendamento.PENDENTE, "editar");
        exigirAntesDoInicio(agora, "editar");
    }

    /** Confirmado e faltando mais de 48h: o cliente ainda pode confirmar ou ajustar restrições e observações. */
    public boolean detalhesEditaveis(LocalDateTime agora) {
        return status == StatusAgendamento.CONFIRMADO && dataHoraInicial != null
                && agora.isBefore(dataHoraInicial.minus(PRAZO_DETALHES));
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

    /**
     * Pedido de um menu: guarda a cópia dele e calcula o fim e o valor. O início já precisa estar definido.
     * A cópia é o contrato do pedido; o menu pode mudar depois.
     */
    public void aplicarMenu(Menu menu, int pessoas) {
        this.menu = menu;
        this.menuNome = menu.getNome();
        this.precoPorPessoa = menu.getPrecoPorPessoa();
        this.duracaoMinutos = menu.getDuracaoMinutos();
        this.ingredientesInclusos = menu.isIngredientesInclusos();
        this.quantidadePessoas = pessoas;
        this.valorTotal = calcularValor(precoPorPessoa, pessoas);
        reposicionarFim();
    }

    /** Muda o início mantendo a duração da cópia do menu. */
    public void mudarInicio(LocalDateTime inicio) {
        this.dataHoraInicial = inicio;
        reposicionarFim();
    }

    private void reposicionarFim() {
        this.dataHoraFinal = dataHoraInicial.plusMinutes(duracaoMinutos);
    }

    /** Preço por pessoa vezes a quantidade, em reais com 2 casas (o mesmo valor que vai para o banco). */
    public static BigDecimal calcularValor(BigDecimal precoPorPessoa, int pessoas) {
        return precoPorPessoa.multiply(BigDecimal.valueOf(pessoas)).setScale(2, RoundingMode.HALF_UP);
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
