package com.couto.chefe_api.domin;

import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Entity()
@Table(name = "agendamentos")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Agendamento  implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)

    private Long id;

    @Column(name = "quantidade_pessoas")
    private Integer quantidadePessoas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserModel usuario;

    @ManyToMany
    @JoinTable(
            name = "agedamento_chefe",
            joinColumns = @JoinColumn(name = "agendamento_id"),
            inverseJoinColumns = @JoinColumn(name = "chefe_id")
    )
    private List<ChefeModel> chefes;

    @ManyToOne
    @JoinColumn(name = "endereco_id")
    private EnderecoModel endereco;

    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime dataHora;

    private StatusAgendamento status = StatusAgendamento.PENDENDE;



    @Column(updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    public void Prepersist(){
        this.criadoEm =LocalDateTime.now();
    }
}
