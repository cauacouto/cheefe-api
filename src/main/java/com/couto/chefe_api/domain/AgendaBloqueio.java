package com.couto.chefe_api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/** Período em que o chef não atende, de {@code inicio} (inclusive) a {@code fim} (exclusive). Some junto com o chef. */
@Entity
@Table(name = "agenda_bloqueio",
        indexes = @Index(name = "idx_agenda_bloqueio_chefe_fim", columnList = "chefe_id, fim"))
@Getter
@Setter
@NoArgsConstructor
public class AgendaBloqueio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChefeModel chefe;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fim;

    @Column(nullable = false)
    private LocalDateTime criadoEm;
}
