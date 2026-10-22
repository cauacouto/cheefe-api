package com.couto.chefe_api.domin;

import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.enums.MotivoDenuncia;
import com.couto.chefe_api.enums.StatusDenuncia;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "denuncia", indexes = {
        @Index(name = "idx_denuncia_chefe_status", columnList = "chefe_id, status"),
        @Index(name = "idx_denuncia_cliente_chefe_status", columnList = "cliente_id, chefe_id, status")})
@Getter
@Setter
@NoArgsConstructor
public class Denuncia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ChefeModel chefe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserModel cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Agendamento agendamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MotivoDenuncia motivo;

    @Column(nullable = false, length = 1000)
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
