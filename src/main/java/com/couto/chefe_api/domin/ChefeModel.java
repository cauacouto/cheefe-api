package com.couto.chefe_api.domin;

import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "chefe")
public class ChefeModel implements Serializable {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)

    private UUID id;

    @NotBlank
    private String nome;

    private String email;

    private String password;

    @NotBlank
    private String nacionalidade;

    @Enumerated(EnumType.STRING)
    private Especialidades especialidade;

    @ManyToMany(mappedBy = "chefes")
    private List<Agendamento> agendamentos = new ArrayList<>();

    @NotNull
    private int anosDeExperiencia;

    @Enumerated(EnumType.STRING)
    private Senioridade senioridade;
@Column(nullable = false)
    private boolean disponivel = true;

private Boolean ativo = true;
    @NotBlank
    private String idiomas;

    private String imageUrl;

    private BigDecimal valorHora;

    @OneToMany(mappedBy = "chefeModel")
    private List<Pratos> pratos = new ArrayList<>();
}
