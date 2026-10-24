package com.couto.chefe_api.domain;

import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.EnderecoModel;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// só as colunas alteradas vão no UPDATE: uma gravação que leu a conta antes (ex.: foto subindo) não
// desfaz contadores somados no banco nem um bloqueio automático que commitou no meio-tempo
@DynamicUpdate
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public  class UserModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)

    private UUID id;
    @NotBlank
    private String nome;
    @Column(nullable = false, unique = true)
    private String email;
    private String telefone;
    private String imageUrl;

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL)
    private List<EnderecoModel> endereco;

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL)
    private List<Agendamento> agendamento;

    /** Bloqueio automático (ódio ou ameaça): a conta não opera até a moderação reverter. */
    private LocalDateTime bloqueadoEm;

    private String bloqueioMotivo;

    /**
     * Celular verificado (E.164), hoje não exigido (verificação parada, ver README); um por conta (V12: índice único parcial).
     * Separado do {@link #telefone} de contato, que o cliente edita livremente.
     */
    @Column(length = 20, unique = true)
    private String telefoneVerificado;

    private LocalDateTime telefoneVerificadoEm;

    /** Soma das notas recebidas e quantas são: a média é soma / total (ver Avaliacao). */
    private int somaNotas;

    private int totalAvaliacoes;
}


