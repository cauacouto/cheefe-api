package com.couto.chefe_api.domin;

import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notificacao", indexes = {
        @Index(name = "idx_notificacao_destinatario", columnList = "destinatarioId, lida, criadaEm"),
        @Index(name = "idx_notificacao_referencia", columnList = "referenciaId, evento")})
@Getter
@Setter
@NoArgsConstructor
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID destinatarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoUsuario destinatarioTipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventoNotificacao evento;

    @Column(nullable = false, length = 300)
    private String mensagem;

    @Column(length = 64)
    private String referenciaId;

    @Column(nullable = false)
    private boolean lida = false;

    @Column(nullable = false)
    private LocalDateTime criadaEm;

    @PrePersist
    void aoCriar() {
        if (criadaEm == null) {
            criadaEm = LocalDateTime.now();
        }
    }
}
