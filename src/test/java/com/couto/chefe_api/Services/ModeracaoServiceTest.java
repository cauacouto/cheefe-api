package com.couto.chefe_api.Services;

import com.couto.chefe_api.Excepitons.ChefeNaoEncontradoException;
import com.couto.chefe_api.Excepitons.ConflitoException;
import com.couto.chefe_api.domin.AcaoModeracao;
import com.couto.chefe_api.enums.TipoAcaoModeracao;
import com.couto.chefe_api.repositorys.AcaoModeracaoRepository;
import org.mockito.ArgumentCaptor;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.Denuncia;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.DenunciaMensagemRepository;
import com.couto.chefe_api.repositorys.DenunciaRepository;
import com.couto.chefe_api.Services.eventos.MensagemApagadaEvent;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.domin.Conversa;
import com.couto.chefe_api.domin.DenunciaMensagem;
import com.couto.chefe_api.domin.Mensagem;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModeracaoServiceTest {

    private static final String MODERADOR = "moderacao@chefhome.test";
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private final DenunciaRepository denunciaRepository = mock(DenunciaRepository.class);
    private final ChefeRepository chefeRepository = mock(ChefeRepository.class);
    private final NotificacaoService notificacaoService = mock(NotificacaoService.class);
    private final AcaoModeracaoRepository acaoRepository = mock(AcaoModeracaoRepository.class);
    private final DenunciaMensagemRepository denunciaMensagemRepository = mock(DenunciaMensagemRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ModeracaoService service = new ModeracaoService(denunciaRepository, chefeRepository, notificacaoService,
            acaoRepository, denunciaMensagemRepository, eventPublisher, RELOGIO);

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
    void cadaAcaoFicaRegistradaComQuemFez() {
        service.arquivar(5L, MODERADOR);
        service.suspender(chefe.getId(), MODERADOR);
        service.reativar(chefe.getId(), MODERADOR);
        service.banir(chefe.getId(), MODERADOR);

        ArgumentCaptor<AcaoModeracao> captor = ArgumentCaptor.forClass(AcaoModeracao.class);
        verify(acaoRepository, times(4)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(AcaoModeracao::getAcao).containsExactly(
                TipoAcaoModeracao.ARQUIVAR_DENUNCIA, TipoAcaoModeracao.SUSPENDER_CHEF,
                TipoAcaoModeracao.REATIVAR_CHEF, TipoAcaoModeracao.BANIR_CHEF);
        assertThat(captor.getAllValues()).allSatisfy(acao -> assertThat(acao.getModeradorEmail()).isEqualTo(MODERADOR));
        assertThat(captor.getAllValues().get(0).getDenunciaId()).isEqualTo(5L);
        assertThat(captor.getAllValues().get(1).getChefeId()).isEqualTo(chefe.getId());
    }

    @Test
    void acaoRecusadaNaoFicaRegistrada() {
        aberta.setStatus(StatusDenuncia.PROCEDENTE);

        assertThatThrownBy(() -> service.arquivar(5L, MODERADOR)).isInstanceOf(ConflitoException.class);
        verify(acaoRepository, never()).save(any());
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

        service.reativar(chefe.getId(), MODERADOR);

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
        assertThatThrownBy(() -> service.reativar(inexistente, MODERADOR)).isInstanceOf(ChefeNaoEncontradoException.class);
        assertThatThrownBy(() -> service.banir(inexistente, MODERADOR)).isInstanceOf(ChefeNaoEncontradoException.class);
    }

    @Test
    void arquivarDenunciaDeMensagemRegistraAuditoria() {
        DenunciaMensagem denuncia = denunciaDeMensagem(30L, mensagemNaConversa(10L, 1L));
        when(denunciaMensagemRepository.findById(30L)).thenReturn(Optional.of(denuncia));

        service.arquivarDenunciaMensagem(30L, MODERADOR);

        assertThat(denuncia.getStatus()).isEqualTo(StatusDenuncia.ARQUIVADA);
        assertThat(denuncia.getResolvidaPor()).isEqualTo(MODERADOR);
        assertThat(denuncia.getResolvidaEm()).isEqualTo(LocalDateTime.now(RELOGIO));
        assertThat(denuncia.getMensagem().isApagada()).isFalse();
        ArgumentCaptor<AcaoModeracao> captor = ArgumentCaptor.forClass(AcaoModeracao.class);
        verify(acaoRepository).save(captor.capture());
        assertThat(captor.getValue().getAcao()).isEqualTo(TipoAcaoModeracao.ARQUIVAR_DENUNCIA_MENSAGEM);
        assertThat(captor.getValue().getDenunciaId()).isEqualTo(30L);
    }

    @Test
    void removerMensagemFechaAsDenunciasAbertasDelaEAvisaOsParticipantes() {
        Mensagem mensagem = mensagemNaConversa(10L, 1L);
        DenunciaMensagem denuncia = denunciaDeMensagem(30L, mensagem);
        DenunciaMensagem outra = denunciaDeMensagem(31L, mensagem);
        when(denunciaMensagemRepository.findById(30L)).thenReturn(Optional.of(denuncia));
        when(denunciaMensagemRepository.findByMensagem_IdAndStatus(10L, StatusDenuncia.ABERTA)).thenReturn(List.of(denuncia, outra));

        service.removerMensagem(30L, MODERADOR);

        assertThat(mensagem.isRemovidaPorModeracao()).isTrue();
        assertThat(mensagem.getTexto()).isEqualTo("original");
        assertThat(List.of(denuncia, outra)).allSatisfy(d -> assertThat(d.getStatus()).isEqualTo(StatusDenuncia.PROCEDENTE));
        verify(eventPublisher).publishEvent(new MensagemApagadaEvent(1L, 10L, CLIENTE_ID, chefe.getId()));
        ArgumentCaptor<AcaoModeracao> captor = ArgumentCaptor.forClass(AcaoModeracao.class);
        verify(acaoRepository).save(captor.capture());
        assertThat(captor.getValue().getAcao()).isEqualTo(TipoAcaoModeracao.REMOVER_MENSAGEM);
        assertThat(captor.getValue().getDenunciaId()).isEqualTo(30L);
    }

    @Test
    void denunciaDeMensagemJaResolvidaDa409ESemAuditoria() {
        DenunciaMensagem denuncia = denunciaDeMensagem(30L, mensagemNaConversa(10L, 1L));
        denuncia.setStatus(StatusDenuncia.ARQUIVADA);
        when(denunciaMensagemRepository.findById(30L)).thenReturn(Optional.of(denuncia));

        assertThatThrownBy(() -> service.arquivarDenunciaMensagem(30L, MODERADOR)).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> service.removerMensagem(30L, MODERADOR)).isInstanceOf(ConflitoException.class);
        verify(acaoRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private static final UUID CLIENTE_ID = UUID.randomUUID();

    private Mensagem mensagemNaConversa(Long mensagemId, Long conversaId) {
        Conversa conversa = new Conversa();
        conversa.setId(conversaId);
        UserModel cliente = new UserModel();
        cliente.setId(CLIENTE_ID);
        conversa.setCliente(cliente);
        conversa.setChefe(chefe);
        Mensagem mensagem = new Mensagem();
        mensagem.setId(mensagemId);
        mensagem.setConversa(conversa);
        mensagem.setTexto("original");
        return mensagem;
    }

    private static DenunciaMensagem denunciaDeMensagem(Long id, Mensagem mensagem) {
        DenunciaMensagem denuncia = new DenunciaMensagem();
        denuncia.setId(id);
        denuncia.setMensagem(mensagem);
        return denuncia;
    }
}
