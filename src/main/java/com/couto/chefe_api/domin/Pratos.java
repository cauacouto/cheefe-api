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
    private String nome;
    private String descricao;
    private String imageUrl;
    @ManyToOne
    @JoinColumn(name = "chefe_id")
    private ChefeModel chefeModel;
}
