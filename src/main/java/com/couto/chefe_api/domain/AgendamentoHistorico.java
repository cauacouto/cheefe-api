package com.couto.chefe_api.domain;

import com.couto.chefe_api.enums.AcaoAgendamento;
import com.couto.chefe_api.enums.TipoUsuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Linha do tempo do agendamento. Só de inserção: no banco a API (chefhome_app) não tem UPDATE nem
 * DELETE nesta tabela (V6); as linhas só somem junto com o agendamento (ON DELETE CASCADE, ex.: LGPD).
 * Autor nulo = o sistema (rotina de conclusão/expiração).
 */
@Entity
@Table(name = "agendamento_historico",
        indexes = @Index(name = "idx_agendamento_historico_agendamento", columnList = "agendamento_id, ocorrido_em"))
@Getter
@Setter
@NoArgsConstructor
public class AgendamentoHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Agendamento agendamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AcaoAgendamento acao;

    private UUID autorId;

    @Enumerated(EnumType.STRING)
    private TipoUsuario autorTipo;

    /** Código de MotivoCancelamento ou MotivoRecusa. */
    private String motivo;

    @Column(length = 300)
    private String descricao;

    @Column(nullable = false)
    private LocalDateTime ocorridoEm;
}
