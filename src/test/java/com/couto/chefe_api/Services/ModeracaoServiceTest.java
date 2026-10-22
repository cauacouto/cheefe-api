package com.couto.chefe_api.Services;

import com.couto.chefe_api.Excepitons.ChefeNaoEncontradoException;
import com.couto.chefe_api.Excepitons.ConflitoException;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.Denuncia;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.DenunciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModeracaoServiceTest {

    private static final String MODERADOR = "moderacao@chefhome.test";

    private final DenunciaRepository denunciaRepository = mock(DenunciaRepository.class);
    private final ChefeRepository chefeRepository = mock(ChefeRepository.class);
    private final NotificacaoService notificacaoService = mock(NotificacaoService.class);
    private final ModeracaoService service = new ModeracaoService(denunciaRepository, chefeRepository, notificacaoService);

    private final ChefeModel chefe = new ChefeModel();
    private final Denuncia aberta = new Denuncia();

    @BeforeEach
    void setUp() {
        chefe.setId(UUID.randomUUID());
        chefe.setNome("Bia");
        aberta.setId(5L);
        aberta.setChefe(chefe);
        when(chefeRepository.findById(chefe.getId())).thenReturn(Optional.of(chefe));
        when(denunciaRepository.findById(5L)).thenReturn(Optional.of(aberta));
        when(denunciaRepository.findByChefe_IdAndStatus(chefe.getId(), StatusDenuncia.ABERTA)).thenReturn(List.of(aberta));
    }

    @Test
    void arquivarGravaQuemResolveu() {
        service.arquivar(5L, MODERADOR);

        assertThat(aberta.getStatus()).isEqualTo(StatusDenuncia.ARQUIVADA);
        assertThat(aberta.getResolvidaPor()).isEqualTo(MODERADOR);
        assertThat(aberta.getResolvidaEm()).isNotNull();
    }

    @Test
    void arquivarResolvidaDa409() {
        aberta.setStatus(StatusDenuncia.PROCEDENTE);

        assertThatThrownBy(() -> service.arquivar(5L, MODERADOR)).isInstanceOf(ConflitoException.class);
    }

    @Test
    void suspenderMarcaProcedentesENotifica() {
        service.suspender(chefe.getId(), MODERADOR);

        assertThat(chefe.isSuspenso()).isTrue();
        assertThat(aberta.getStatus()).isEqualTo(StatusDenuncia.PROCEDENTE);
        assertThat(aberta.getResolvidaPor()).isEqualTo(MODERADOR);
        verify(notificacaoService).notificar(chefe.getId(), TipoUsuario.CHEFE, EventoNotificacao.CHEF_SUSPENSO,
                "Seu perfil foi suspenso", chefe.getId().toString());
    }

    @Test
    void reativarNotifica() {
        chefe.setSuspenso(true);

        service.reativar(chefe.getId());

        assertThat(chefe.isSuspenso()).isFalse();
        verify(notificacaoService).notificar(chefe.getId(), TipoUsuario.CHEFE, EventoNotificacao.CHEF_REATIVADO,
                "Seu perfil foi reativado", chefe.getId().toString());
    }

    @Test
    void banirDesativaEMarcaProcedentes() {
        service.banir(chefe.getId(), MODERADOR);

        assertThat(chefe.getAtivo()).isFalse();
        assertThat(aberta.getStatus()).isEqualTo(StatusDenuncia.PROCEDENTE);
    }

    @Test
    void chefInexistenteDa404() {
        UUID inexistente = UUID.randomUUID();

        assertThatThrownBy(() -> service.suspender(inexistente, MODERADOR)).isInstanceOf(ChefeNaoEncontradoException.class);
        assertThatThrownBy(() -> service.reativar(inexistente)).isInstanceOf(ChefeNaoEncontradoException.class);
        assertThatThrownBy(() -> service.banir(inexistente, MODERADOR)).isInstanceOf(ChefeNaoEncontradoException.class);
    }
}
