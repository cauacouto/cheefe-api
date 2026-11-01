package com.couto.chefe_api.domain;

import com.couto.chefe_api.exception.ContaInativaException;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
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
@Table(name = "chefe", indexes = @Index(name = "idx_chefe_cidade", columnList = "estado, cidade_busca"))
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

    @OneToMany(mappedBy = "chefe")
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

    /** Celular verificado (E.164), um por conta (V12). Hoje não é exigido: a verificação está parada (README). */
    @Column(length = 20, unique = true)
    private String telefoneVerificado;

    private LocalDateTime telefoneVerificadoEm;

    /** Soma das notas recebidas e quantas são: a média é soma / total (ver Avaliacao). */
    private int somaNotas;

    private int totalAvaliacoes;
    private String idiomas;

    private String imageUrl;

    private BigDecimal valorHora;

    /** Cidade de origem: o chef atende só nela (o endereço do agendamento precisa ser da mesma cidade e UF). */
    @Column(length = 100)
    private String cidade;

    /** UF da cidade de origem, em maiúsculas. */
    @Column(length = 2)
    private String estado;

    /** A cidade sem acento, em minúsculas e com espaços únicos: o que a busca e o agendamento comparam. */
    @Column(name = "cidade_busca", length = 100)
    @Setter(AccessLevel.NONE)
    private String cidadeBusca;

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
        if (cidade == null) pendentes.add("cidade");
        if (estado == null) pendentes.add("estado");
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

    /**
     * A cidade de busca acompanha a cidade no próprio setter (e não num @PreUpdate): com @DynamicUpdate, um campo
     * mudado só no callback poderia ficar fora do UPDATE.
     */
    public void setCidade(String cidade) {
        this.cidade = semEspacosOuNulo(cidade);
        this.cidadeBusca = Cidade.normalizar(this.cidade);
    }

    public void setEstado(String estado) {
        this.estado = Cidade.uf(estado);
    }

    /** O chef atende nesta cidade (a de origem dele). */
    public boolean atendeEm(String cidade, String estado) {
        return Cidade.mesma(this.cidade, this.estado, cidade, estado);
    }

    private static String semEspacosOuNulo(String valor) {
        return emBranco(valor) ? null : valor.strip();
    }
}
