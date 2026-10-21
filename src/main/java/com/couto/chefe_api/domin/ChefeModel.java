package com.couto.chefe_api.domin;

import com.couto.chefe_api.Excepitons.ContaInativaException;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
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

    @Column(nullable = false, unique = true)
    private String email;

    private String password;

    private String nacionalidade;

    @Enumerated(EnumType.STRING)
    private Especialidades especialidade;

    @ManyToMany(mappedBy = "chefes")
    private List<Agendamento> agendamentos = new ArrayList<>();

    private Integer anosDeExperiencia;

    @Enumerated(EnumType.STRING)
    private Senioridade senioridade;
@Column(nullable = false)
    private boolean disponivel = true;

private Boolean ativo = true;
    private String idiomas;

    private String imageUrl;

    private BigDecimal valorHora;

    @OneToMany(mappedBy = "chefeModel")
    private List<Pratos> pratos = new ArrayList<>();

    /** Conta apagada não opera, mesmo com token ainda válido. */
    public void exigirAtivo() {
        if (!Boolean.TRUE.equals(ativo)) {
            throw new ContaInativaException();
        }
    }

    /** Campos que o chef precisa preencher para aparecer na busca e ser agendado, na ordem do contrato. */
    public List<String> camposPendentes() {
        List<String> pendentes = new ArrayList<>();
        if (emBranco(nacionalidade)) pendentes.add("nacionalidade");
        if (especialidade == null) pendentes.add("especialidade");
        if (senioridade == null) pendentes.add("senioridade");
        if (anosDeExperiencia == null) pendentes.add("anosDeExperiencia");
        if (emBranco(idiomas)) pendentes.add("idiomas");
        if (valorHora == null) pendentes.add("valorHora");
        return pendentes;
    }

    public boolean isPerfilCompleto() {
        return camposPendentes().isEmpty();
    }

    private static boolean emBranco(String valor) {
        return valor == null || valor.isBlank();
    }

    /**
     * Texto só com espaços (inclusive tab e espaços Unicode, que o TRIM do banco não remove)
     * vira null ao gravar. Assim a busca (SQL) e isPerfilCompleto (Java) concordam.
     */
    @PrePersist
    @PreUpdate
    void normalizarTextos() {
        nacionalidade = semEspacosOuNulo(nacionalidade);
        idiomas = semEspacosOuNulo(idiomas);
    }

    private static String semEspacosOuNulo(String valor) {
        return emBranco(valor) ? null : valor.strip();
    }
}
