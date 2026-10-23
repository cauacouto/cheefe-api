package com.couto.chefe_api.domain;

import com.couto.chefe_api.enums.MotivoDenunciaAvaliacao;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.enums.TipoUsuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

/** Denúncia de um comentário de avaliação; guarda a cópia do texto no momento da denúncia. */
@Entity
@Table(name = "denuncia_avaliacao",
        uniqueConstraints = @UniqueConstraint(name = "uk_denuncia_avaliacao_denunciante",
                columnNames = {"avaliacao_id", "denunciante_id"}),
        indexes = @Index(name = "idx_denuncia_avaliacao_status", columnList = "status, criada_em"))
@Getter
@Setter
@NoArgsConstructor
public class DenunciaAvaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Avaliacao avaliacao;

    @Column(nullable = false)
    private UUID denuncianteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoUsuario denuncianteTipo;

    @Column(nullable = false)
    private UUID autorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoUsuario autorTipo;

    @Column(nullable = false, length = 1000)
    private String textoDenunciado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MotivoDenunciaAvaliacao motivo;

    @Column(length = 1000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusDenuncia status = StatusDenuncia.ABERTA;

    @Column(nullable = false)
    private LocalDateTime criadaEm;

    private LocalDateTime resolvidaEm;

    private String resolvidaPor;

    @PrePersist
    void aoCriar() {
        if (criadaEm == null) {
            criadaEm = LocalDateTime.now();
        }
    }
}
