package com.couto.chefe_api.domain;

import com.couto.chefe_api.enums.CategoriaConteudo;
import com.couto.chefe_api.enums.StatusBloqueio;
import com.couto.chefe_api.enums.TipoUsuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Fila de moderação do bloqueio automático: o texto que bloqueou a conta e a decisão da moderação. */
@Entity
@Table(name = "bloqueio_conta",
        indexes = @Index(name = "idx_bloqueio_conta_status", columnList = "status, criado_em"))
@Getter
@Setter
@NoArgsConstructor
public class BloqueioConta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID contaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoUsuario contaTipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaConteudo categoria;

    @Column(nullable = false)
    private String termo;

    @Column(nullable = false, length = 2000)
    private String texto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusBloqueio status = StatusBloqueio.ABERTO;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    private LocalDateTime resolvidoEm;

    private String resolvidoPor;

    @PrePersist
    void aoCriar() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }
}
