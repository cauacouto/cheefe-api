package com.couto.chefe_api.domin;

import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.enums.TipoDeResidencia;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "endereco")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Endereço implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)

    private Long id;
    private String rua;
    private String numero;
    private String bairro;
    private String cidade;
    private TipoDeResidencia tipoDeResidencia;
    private String complemento;
    private String estado;
    private String cep;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserModel userModel;
}
