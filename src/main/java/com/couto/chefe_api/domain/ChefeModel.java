package com.couto.chefe_api.domain;

import com.couto.chefe_api.exception.ContaInativaException;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

// só as colunas alteradas vão no UPDATE: uma gravação que leu a conta antes (ex.: foto subindo) não
// desfaz contadores somados no banco nem um bloqueio automático que commitou no meio-tempo
@DynamicUpdate
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

    /** Suspenso pela moderação: some da busca e não pode ser agendado, mas continua logando. */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean suspenso;

    /** Bloqueio automático (ódio ou ameaça): a conta não opera até a moderação reverter. */
    private LocalDateTime bloqueadoEm;

    private String bloqueioMotivo;

    /** Celular verificado por SMS (E.164): sem ele o chef não age nem aparece; um por conta (V12). */
    @Column(length = 20, unique = true)
    private String telefoneVerificado;

    private LocalDateTime telefoneVerificadoEm;

    /** Soma das notas recebidas e quantas são: a média é soma / total (ver Avaliacao). */
    private int somaNotas;

    private int totalAvaliacoes;
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
