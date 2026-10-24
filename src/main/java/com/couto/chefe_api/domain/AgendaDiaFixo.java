package com.couto.chefe_api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.DayOfWeek;

/** Dia da semana em que o chef nunca atende (ex.: toda segunda). Some junto com o chef. */
@Entity
@Table(name = "agenda_dia_fixo")
@Getter
@NoArgsConstructor
public class AgendaDiaFixo {

    @EmbeddedId
    private AgendaDiaFixoId id;

    @MapsId("chefeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChefeModel chefe;

    public AgendaDiaFixo(ChefeModel chefe, DayOfWeek dia) {
        this.id = new AgendaDiaFixoId(chefe.getId(), dia);
        this.chefe = chefe;
    }

    public DayOfWeek getDiaSemana() {
        return id.getDiaSemana();
    }
}
