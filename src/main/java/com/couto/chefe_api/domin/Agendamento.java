package com.couto.chefe_api.domin;

import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.enums.StatusAgendamento;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Entity(name = "agendamento")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Agendamento  implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)

    private Long id;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endereco_id")
    private EnderecoModel endereco;

    @Column(nullable = false)
    private LocalDateTime datahora;

    private StatusAgendamento status = StatusAgendamento.PENDENDE;
    @CreationTimestamp
    private LocalDateTime criado;
}
