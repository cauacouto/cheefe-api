package com.couto.chefe_api.domain;

import com.couto.chefe_api.enums.TipoUsuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Avaliação de um agendamento concluído, em uma das duas direções (cliente avalia chef, chef avalia
 * cliente). Autor e avaliado são ids soltos + tipo, como nas mensagens do chat. Some junto com o
 * agendamento (ON DELETE CASCADE, ex.: LGPD).
 */
// só as colunas alteradas vão no UPDATE: responder ao mesmo tempo em que a moderação remove o
// comentário não devolve o comentário
@DynamicUpdate
@Entity
@Table(name = "avaliacao",
        uniqueConstraints = @UniqueConstraint(name = "uk_avaliacao_agendamento_autor_avaliado",
                columnNames = {"agendamento_id", "autor_id", "avaliado_id"}),
        indexes = @Index(name = "idx_avaliacao_avaliado_publicado", columnList = "avaliado_id, publicado_em"))
@Getter
@Setter
@NoArgsConstructor
public class Avaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Agendamento agendamento;

    @Column(nullable = false)
    private UUID autorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoUsuario autorTipo;

    @Column(nullable = false)
    private UUID avaliadoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoUsuario avaliadoTipo;

    @Column(nullable = false)
    private int nota;

    @Column(length = 1000)
    private String comentario;

    /** Texto como foi enviado, para a moderação, caso o comentário seja removido. */
    @Column(length = 1000)
    private String comentarioOriginal;

    @Column(nullable = false)
    private boolean anonimo = true;

    /** Nulo enquanto o comentário com nome espera o chef avaliar o cliente (ou os 7 dias). */
    private LocalDateTime publicadoEm;

    @Column(nullable = false)
    private boolean comentarioRemovido;

    @Column(length = 1000)
    private String resposta;

    @Column(length = 1000)
    private String respostaOriginal;

    private LocalDateTime respondidaEm;

    @Column(nullable = false)
    private LocalDateTime criadaEm;

    @ElementCollection
    @CollectionTable(name = "avaliacao_tag", joinColumns = @JoinColumn(name = "avaliacao_id"))
    @Column(name = "tag", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @BatchSize(size = 50) // a página pública carrega as tags de todas de uma vez
    private Set<String> tags = new HashSet<>();

    @PrePersist
    void aoCriar() {
        if (criadaEm == null) {
            criadaEm = LocalDateTime.now();
        }
    }
}
