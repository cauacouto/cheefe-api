package com.couto.chefe_api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pratos")
@BatchSize(size = 50) // os pratos de uma lista de menus carregam juntos, não um a um
public class Pratos {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    @Column(name = "nome_prato")
    private String nomePrato;
    private String descricao;
    @Column(name = "image_url")
    private String imageUrl;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chefe_id", nullable = false)
    private ChefeModel chefeModel;
}
