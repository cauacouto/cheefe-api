package com.couto.chefe_api.domain;

import com.couto.chefe_api.enums.EtapaMenu;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Prato num menu, com a etapa em que é servido: o mesmo prato pode ser entrada num menu e principal em outro. */
@Entity
@Table(name = "menu_item")
@Getter
@NoArgsConstructor
public class MenuItem {

    @EmbeddedId
    private MenuItemId id = new MenuItemId();

    @MapsId("menuId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Menu menu;

    @MapsId("pratoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Pratos prato;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EtapaMenu etapa;

    @Column(nullable = false)
    private int ordem;

    public MenuItem(Menu menu, Pratos prato, EtapaMenu etapa, int ordem) {
        this.menu = menu;
        this.prato = prato;
        this.etapa = etapa;
        this.ordem = ordem;
    }

    public void reposicionar(EtapaMenu etapa, int ordem) {
        this.etapa = etapa;
        this.ordem = ordem;
    }
}
