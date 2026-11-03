package com.couto.chefe_api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/** Anotação privada do chef sobre um cliente: só o próprio chef lê. Some junto com a conta de qualquer um dos dois. */
@Entity
@Table(name = "nota_cliente")
@Getter
@Setter
@NoArgsConstructor
public class NotaCliente {

    @EmbeddedId
    private NotaClienteId id;

    @MapsId("chefeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chefe_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChefeModel chefe;

    @MapsId("clienteId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserModel cliente;

    /** Como ficou depois do filtro (palavrão e contato viram "***"). */
    @Column(nullable = false, length = 2000)
    private String texto;

    /** O que o chef escreveu, quando o exibido em {@link #texto} foi mascarado. Nunca vai para a API. */
    @Column(length = 2000)
    private String textoOriginal;

    @Column(nullable = false)
    private LocalDateTime atualizadaEm;
}
