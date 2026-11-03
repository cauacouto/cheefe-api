package com.couto.chefe_api.domain;

import com.couto.chefe_api.enums.OcasiaoMenu;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Menu do chef: pratos por etapa a um preço por pessoa. Não se exclui (agendamentos apontam para ele): pausa-se.
 * Some junto com o chef (ON DELETE CASCADE).
 */
// só as colunas alteradas vão no UPDATE: pausar ao mesmo tempo em que o chef edita não desfaz a edição (nem o contrário)
@DynamicUpdate
@Entity
@Table(name = "menu", indexes = @Index(name = "idx_menu_chefe_ativo", columnList = "chefe_id, ativo"))
@Getter
@Setter
@NoArgsConstructor
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChefeModel chefe;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(length = 1000)
    private String descricao;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoPorPessoa;

    @Column(nullable = false)
    private int minimoConvidados;

    @Column(nullable = false)
    private int maximoConvidados;

    @Column(nullable = false)
    private int duracaoMinutos;

    @Column(nullable = false)
    private boolean ingredientesInclusos;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    @ElementCollection
    @CollectionTable(name = "menu_ocasiao", joinColumns = @JoinColumn(name = "menu_id"),
            indexes = @Index(name = "idx_menu_ocasiao_ocasiao", columnList = "ocasiao"))
    @Enumerated(EnumType.STRING)
    @Column(name = "ocasiao", nullable = false, length = 32)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @BatchSize(size = 50) // a lista de menus carrega as ocasiões de todos de uma vez
    private Set<OcasiaoMenu> ocasioes = EnumSet.noneOf(OcasiaoMenu.class);

    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<MenuItem> itens = new ArrayList<>();
}
