package com.couto.chefe_api.User;

import com.couto.chefe_api.enums.TipoDeResidencia;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.NotBlank;

import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)

    private UUID id ;
    @NotBlank
    private String nome;
    @NotBlank
    private String logradouro;
    @NotBlank
    private String cidade;
    private TipoDeResidencia tipoDeResidencia;
    @NotBlank
    private String complemento;
}
