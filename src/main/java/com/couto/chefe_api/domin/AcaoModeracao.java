package com.couto.chefe_api.domin;

import com.couto.chefe_api.enums.TipoAcaoModeracao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Rastro de cada ação da moderação. Só de inserção: no banco, a API (chefhome_app) não tem
 * UPDATE nem DELETE nesta tabela (V2). Sem FKs de propósito: o registro sobrevive à exclusão
 * da denúncia ou do chef.
 */
@Entity
@Table(name = "acao_moderacao")
@Getter
@Setter
@NoArgsConstructor
public class AcaoModeracao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String moderadorEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoAcaoModeracao acao;

    private UUID chefeId;

    /** Id em denuncia ou, nas ações *_MENSAGEM, em denuncia_mensagem. */
    private Long denunciaId;

    @Column(nullable = false)
    private LocalDateTime realizadaEm;

    @PrePersist
    void aoCriar() {
        realizadaEm = LocalDateTime.now();
    }
}
