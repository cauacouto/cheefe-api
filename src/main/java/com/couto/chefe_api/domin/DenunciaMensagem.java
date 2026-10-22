package com.couto.chefe_api.domin;

import com.couto.chefe_api.enums.MotivoDenunciaMensagem;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.enums.TipoUsuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Denúncia de uma mensagem do chat; guarda a cópia do texto no momento da denúncia. */
@Entity
@Table(name = "denuncia_mensagem",
        uniqueConstraints = @UniqueConstraint(name = "uk_denuncia_mensagem_denunciante",
                columnNames = {"mensagem_id", "denunciante_id"}),
        indexes = @Index(name = "idx_denuncia_mensagem_status", columnList = "status, criada_em"))
@Getter
@Setter
@NoArgsConstructor
public class DenunciaMensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Mensagem mensagem;

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

    @Column(nullable = false, length = 2000)
    private String textoDenunciado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MotivoDenunciaMensagem motivo;

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
        criadaEm = LocalDateTime.now();
    }
}
