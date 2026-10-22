package com.couto.chefe_api.service;

import com.couto.chefe_api.dto.DenunciaCriadaDto;
import com.couto.chefe_api.dto.DenunciaMensagemRequestDto;
import com.couto.chefe_api.exception.ConflitoException;
import com.couto.chefe_api.exception.ContaInativaException;
import com.couto.chefe_api.exception.RecursoNaoEncontradoException;
import com.couto.chefe_api.security.ModeracaoRegistry;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Conversa;
import com.couto.chefe_api.domain.DenunciaMensagem;
import com.couto.chefe_api.domain.Mensagem;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.MotivoDenunciaMensagem;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.ConversaRepository;
import com.couto.chefe_api.repository.DenunciaMensagemRepository;
import com.couto.chefe_api.repository.MensagemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DenunciaMensagemServiceTest {

    private final ConversaRepository conversaRepository = mock(ConversaRepository.class);
    private final MensagemRepository mensagemRepository = mock(MensagemRepository.class);
    private final DenunciaMensagemRepository denunciaRepository = mock(DenunciaMensagemRepository.class);
    private final NotificacaoService notificacaoService = mock(NotificacaoService.class);
    private final ModeracaoRegistry moderacao = new ModeracaoRegistry("m1@x.com,m2@x.com");
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final DenunciaMensagemService service = new DenunciaMensagemService(conversaRepository, mensagemRepository,
            denunciaRepository, notificacaoService, moderacao, RELOGIO);
    private ChefeModel chefe;

    private final Mensagem mensagem = new Mensagem();
    private UsuarioAutenticado ana;
    private UsuarioAutenticado bia;

    @BeforeEach
    void setUp() {
        UserModel cliente = new UserModel();
        cliente.setId(UUID.randomUUID());
        chefe = new ChefeModel();
        chefe.setId(UUID.randomUUID());
        Conversa conversa = new Conversa();
        conversa.setId(1L);
        conversa.setCliente(cliente);
        conversa.setChefe(chefe);
        ana = new UsuarioAutenticado(cliente.getId(), "ana@x.com", TipoUsuario.CLIENTE);
        bia = new UsuarioAutenticado(chefe.getId(), "bia@x.com", TipoUsuario.CHEFE);

        mensagem.setId(10L);
        mensagem.setConversa(conversa);
        mensagem.setRemetenteId(chefe.getId());
        mensagem.setRemetenteTipo(TipoUsuario.CHEFE);
        mensagem.setTexto("texto ofensivo");
        mensagem.setEnviadaEm(LocalDateTime.now());
        when(conversaRepository.findById(1L)).thenReturn(Optional.of(conversa));
        when(mensagemRepository.findByIdAndConversa_Id(10L, 1L)).thenReturn(Optional.of(mensagem));
        when(denunciaRepository.save(any())).thenAnswer(inv -> {
            DenunciaMensagem d = inv.getArgument(0);
            d.setId(77L);
            return d;
        });
    }

    @Test
    void destinatarioDenunciaComCopiaDoTextoEModeradoresSaoNotificados() {
        DenunciaCriadaDto criada = service.denunciar(1L, 10L, ana,
                new DenunciaMensagemRequestDto(MotivoDenunciaMensagem.OFENSIVA, "  me xingou  "));

        assertThat(criada).isEqualTo(new DenunciaCriadaDto(77L, StatusDenuncia.ABERTA));
        ArgumentCaptor<DenunciaMensagem> captor = ArgumentCaptor.forClass(DenunciaMensagem.class);
        verify(denunciaRepository).save(captor.capture());
        DenunciaMensagem denuncia = captor.getValue();
        assertThat(denuncia.getTextoDenunciado()).isEqualTo("texto ofensivo");
        assertThat(denuncia.getDenuncianteId()).isEqualTo(ana.id());
        assertThat(denuncia.getDenuncianteTipo()).isEqualTo(TipoUsuario.CLIENTE);
        assertThat(denuncia.getAutorId()).isEqualTo(bia.id());
        assertThat(denuncia.getAutorTipo()).isEqualTo(TipoUsuario.CHEFE);
        assertThat(denuncia.getDescricao()).isEqualTo("me xingou");
        assertThat(denuncia.getCriadaEm()).isEqualTo(LocalDateTime.now(RELOGIO));
        moderacao.ids().forEach(id -> verify(notificacaoService).notificar(eq(id), eq(TipoUsuario.MODERADOR),
                eq(EventoNotificacao.MENSAGEM_DENUNCIADA), eq("Nova denúncia de mensagem (OFENSIVA)"), eq("77")));
    }

    @Test
    void descricaoEmBrancoViraNula() {
        service.denunciar(1L, 10L, ana, new DenunciaMensagemRequestDto(MotivoDenunciaMensagem.SPAM, "   "));

        ArgumentCaptor<DenunciaMensagem> captor = ArgumentCaptor.forClass(DenunciaMensagem.class);
        verify(denunciaRepository).save(captor.capture());
        assertThat(captor.getValue().getDescricao()).isNull();
    }

    @Test
    void autorNaoDenunciaAPropria() {
        assertThatThrownBy(() -> service.denunciar(1L, 10L, bia, pedido())).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(denunciaRepository, never()).save(any());
    }

    @Test
    void naoParticipanteRecebe404() {
        UsuarioAutenticado intruso = new UsuarioAutenticado(UUID.randomUUID(), "x@x.com", TipoUsuario.CLIENTE);

        assertThatThrownBy(() -> service.denunciar(1L, 10L, intruso, pedido())).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void denunciaRepetidaDa409() {
        when(denunciaRepository.existsByMensagem_IdAndDenuncianteId(10L, ana.id())).thenReturn(true);

        assertThatThrownBy(() -> service.denunciar(1L, 10L, ana, pedido())).isInstanceOf(ConflitoException.class);
        verify(denunciaRepository, never()).save(any());
    }

    @Test
    void mensagemApagadaDa409() {
        mensagem.setApagadaEm(LocalDateTime.now());

        assertThatThrownBy(() -> service.denunciar(1L, 10L, ana, pedido())).isInstanceOf(ConflitoException.class);
    }

    private static DenunciaMensagemRequestDto pedido() {
        return new DenunciaMensagemRequestDto(MotivoDenunciaMensagem.ASSEDIO, null);
    }

    @Test
    void chefApagadoNaoDenuncia() {
        mensagem.setRemetenteId(ana.id());
        mensagem.setRemetenteTipo(TipoUsuario.CLIENTE);
        chefe.setAtivo(false);

        assertThatThrownBy(() -> service.denunciar(1L, 10L, bia, pedido())).isInstanceOf(ContaInativaException.class);
        verify(denunciaRepository, never()).save(any());
    }
}
