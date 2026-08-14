package com.couto.chefe_api.User;

import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.EnderecoModel;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

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
}


