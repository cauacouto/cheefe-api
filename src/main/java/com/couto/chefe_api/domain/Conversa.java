package com.couto.chefe_api.domain;

import com.couto.chefe_api.domain.UserModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Uma conversa por par cliente–chef; só o cliente inicia. */
@Entity
@Table(name = "conversa", uniqueConstraints = @UniqueConstraint(name = "uk_conversa_cliente_chefe",
        columnNames = {"cliente_id", "chefe_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Conversa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserModel cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ChefeModel chefe;

    @Column(nullable = false)
    private LocalDateTime criadaEm;

    @Column(nullable = false)
    private LocalDateTime ultimaMensagemEm;

    @PrePersist
    void aoCriar() {
        if (criadaEm == null) {
            criadaEm = LocalDateTime.now();
        }
        if (ultimaMensagemEm == null) {
            ultimaMensagemEm = criadaEm;
        }
    }
}
