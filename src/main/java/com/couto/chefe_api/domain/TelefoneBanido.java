package com.couto.chefe_api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Telefone de conta banida, só como hash (SHA-256 de pepper + E.164): sem número e sem id da conta, fica
 * depois que a conta é apagada e impede o recadastro com o mesmo celular (prevenção de fraude).
 */
@Entity
@Table(name = "telefone_banido")
@Getter
@Setter
@NoArgsConstructor
public class TelefoneBanido {

    @Id
    @Column(length = 64)
    private String hash;

    @Column(nullable = false)
    private LocalDateTime banidoEm;
}
