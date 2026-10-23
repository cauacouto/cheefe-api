package com.couto.chefe_api.domain;

import com.couto.chefe_api.enums.TipoUsuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Apagar (pelo autor) ou remover (pela moderação) não apaga o texto da tabela: ele continua
 * disponível como prova para denúncias; quem lê a conversa só vê "mensagem apagada".
 */
@Entity
@Table(name = "mensagem", indexes = @Index(name = "idx_mensagem_conversa", columnList = "conversa_id, id"))
@Getter
@Setter
@NoArgsConstructor
public class Mensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Conversa conversa;

    @Column(nullable = false)
    private UUID remetenteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoUsuario remetenteTipo;

    @Column(nullable = false, length = 2000)
    private String texto;

    /** Como foi escrito, quando o filtro mascarou algo; só a moderação vê. Nulo = igual ao texto. */
    @Column(length = 2000)
    private String textoOriginal;

    @Column(nullable = false)
    private LocalDateTime enviadaEm;

    private LocalDateTime lidaEm;

    private LocalDateTime editadaEm;

    private LocalDateTime apagadaEm;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean removidaPorModeracao;

    public boolean isApagada() {
        return apagadaEm != null || removidaPorModeracao;
    }
}
