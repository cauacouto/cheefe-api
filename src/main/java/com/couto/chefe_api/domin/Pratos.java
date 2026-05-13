package com.couto.chefe_api.domin;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pratos")
public class Pratos {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    @Column(name = "nome_prato")
    private String nomePrato;
    private String descricao;
    @Column(name = "image_Url")
    private String imageUrl;
    @ManyToOne(optional = false)
    @JoinColumn(name = "chefe_id", nullable = false)
    private ChefeModel chefeModel;
}
