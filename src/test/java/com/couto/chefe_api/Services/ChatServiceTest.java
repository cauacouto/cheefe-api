package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.MensagemDto;
import com.couto.chefe_api.Excepitons.ConflitoException;
import com.couto.chefe_api.Excepitons.RecursoNaoEncontradoException;
import com.couto.chefe_api.Excepitons.RegraDeNegocioException;
import com.couto.chefe_api.Security.UsuarioAutenticado;
import com.couto.chefe_api.Services.eventos.MensagemApagadaEvent;
import com.couto.chefe_api.Services.eventos.MensagemEditadaEvent;
import com.couto.chefe_api.Services.eventos.MensagemEnviadaEvent;
import com.couto.chefe_api.Services.eventos.MensagensLidasEvent;
import org.mockito.ArgumentCaptor;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.Conversa;
import com.couto.chefe_api.domin.Mensagem;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.ConversaRepository;
import com.couto.chefe_api.repositorys.MensagemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

class ChatServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZONA);
    private static final LocalDateTime AGORA = LocalDateTime.now(RELOGIO);

    private final ConversaRepository conversaRepository = mock(ConversaRepository.class);
    private final MensagemRepository mensagemRepository = mock(MensagemRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ChatService service = new ChatService(conversaRepository, mensagemRepository,
            mock(UsuarioRepository.class), mock(ChefeRepository.class), mock(NotificacaoService.class), eventPublisher, RELOGIO);

    private final UserModel cliente = new UserModel();
    private final ChefeModel chefe = new ChefeModel();
    private final Conversa conversa = new Conversa();
    private final Mensagem mensagem = new Mensagem();
    private UsuarioAutenticado ana;
    private UsuarioAutenticado bia;

    @BeforeEach
    void setUp() {
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Ana");
        chefe.setId(UUID.randomUUID());
        chefe.setNome("Bia");
        conversa.setId(1L);
        conversa.setCliente(cliente);
        conversa.setChefe(chefe);
        ana = new UsuarioAutenticado(cliente.getId(), "ana@x.com", TipoUsuario.CLIENTE);
        bia = new UsuarioAutenticado(chefe.getId(), "bia@x.com", TipoUsuario.CHEFE);

        mensagem.setId(10L);
        mensagem.setConversa(conversa);
        mensagem.setRemetenteId(cliente.getId());
        mensagem.setRemetenteTipo(TipoUsuario.CLIENTE);
        mensagem.setTexto("original");
        mensagem.setEnviadaEm(AGORA.minusMinutes(5));
        when(conversaRepository.findById(1L)).thenReturn(Optional.of(conversa));
        when(mensagemRepository.findByIdAndConversa_Id(10L, 1L)).thenReturn(Optional.of(mensagem));
    }

    @Test
    void editarDentroDoPrazo() {
        MensagemDto dto = service.editar(1L, 10L, ana, "  corrigido  ");

        assertThat(mensagem.getTexto()).isEqualTo("corrigido");
        assertThat(mensagem.getEditadaEm()).isEqualTo(AGORA);
        assertThat(dto.editada()).isTrue();
        assertThat(dto.texto()).isEqualTo("corrigido");
        verify(eventPublisher).publishEvent(new MensagemEditadaEvent(ana.id(), dto, bia.id(), MensagemDto.de(mensagem, bia.id())));
    }

    @Test
    void editarNoLimiteDos15MinAindaPode() {
        mensagem.setEnviadaEm(AGORA.minusMinutes(15));

        service.editar(1L, 10L, ana, "ainda dá");

        assertThat(mensagem.getTexto()).isEqualTo("ainda dá");
    }

    @Test
    void editarDepoisDe15MinDa409() {
        mensagem.setEnviadaEm(AGORA.minusMinutes(16));

        assertThatThrownBy(() -> service.editar(1L, 10L, ana, "tarde demais")).isInstanceOf(ConflitoException.class);
        assertThat(mensagem.getTexto()).isEqualTo("original");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void editarEmBrancoDa400() {
        assertThatThrownBy(() -> service.editar(1L, 10L, ana, "   ")).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void editarOuApagarDeOutroDa404() {
        assertThatThrownBy(() -> service.editar(1L, 10L, bia, "não é minha")).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.apagar(1L, 10L, bia)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(mensagem.isApagada()).isFalse();
        assertThat(mensagem.getTexto()).isEqualTo("original");
    }

    @Test
    void mensagemDeOutraConversaDa404() {
        when(mensagemRepository.findByIdAndConversa_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apagar(1L, 99L, ana)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void apagarEhIdempotente() {
        service.apagar(1L, 10L, ana);
        LocalDateTime primeira = mensagem.getApagadaEm();
        service.apagar(1L, 10L, ana);

        assertThat(primeira).isEqualTo(AGORA);
        assertThat(mensagem.getApagadaEm()).isEqualTo(primeira);
        verify(eventPublisher, times(1)).publishEvent(new MensagemApagadaEvent(1L, 10L, cliente.getId(), chefe.getId()));
    }

    @Test
    void editarApagadaDa409() {
        service.apagar(1L, 10L, ana);

        assertThatThrownBy(() -> service.editar(1L, 10L, ana, "ressuscitar")).isInstanceOf(ConflitoException.class);
    }

    @Test
    void mensagemApagadaVemSemTexto() {
        service.apagar(1L, 10L, ana);

        MensagemDto dto = MensagemDto.de(mensagem, bia.id());
        assertThat(dto.texto()).isNull();
        assertThat(dto.apagada()).isTrue();
        assertThat(mensagem.getTexto()).isEqualTo("original");
    }

    @Test
    void enviarPublicaAMensagemDoPontoDeVistaDeCadaUm() {
        when(mensagemRepository.save(any())).thenAnswer(inv -> {
            Mensagem m = inv.getArgument(0);
            m.setId(11L);
            return m;
        });

        MensagemDto dto = service.enviar(1L, ana, "oi");

        ArgumentCaptor<MensagemEnviadaEvent> captor = ArgumentCaptor.forClass(MensagemEnviadaEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        MensagemEnviadaEvent evento = captor.getValue();
        assertThat(evento.remetenteId()).isEqualTo(ana.id());
        assertThat(evento.paraRemetente()).isEqualTo(dto);
        assertThat(evento.paraRemetente().minha()).isTrue();
        assertThat(evento.destinatarioId()).isEqualTo(bia.id());
        assertThat(evento.paraDestinatario().minha()).isFalse();
    }

    @Test
    void marcarLidasAvisaQuemEnviou() {
        when(mensagemRepository.findFirstByConversa_IdOrderByIdDesc(1L)).thenReturn(Optional.of(mensagem));
        when(mensagemRepository.marcarLidas(1L, bia.id(), 10L, AGORA)).thenReturn(2);

        service.marcarLidas(1L, bia);

        // o "visto" vai só até a última mensagem que existia antes do UPDATE (nada chega depois sem lidaEm)
        verify(mensagemRepository).marcarLidas(1L, bia.id(), 10L, AGORA);
        verify(eventPublisher).publishEvent(new MensagensLidasEvent(1L, bia.id(), ana.id(), 10L, AGORA));
    }

    @Test
    void marcarLidasSemNadaNovoNaoAvisa() {
        when(mensagemRepository.findFirstByConversa_IdOrderByIdDesc(1L)).thenReturn(Optional.of(mensagem));
        when(mensagemRepository.marcarLidas(any(), any(), any(), any())).thenReturn(0);

        service.marcarLidas(1L, bia);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void previaNaoCortaEmojiAoMeio() {
        mensagem.setTexto("a".repeat(99) + "😀😀");
        when(conversaRepository.findByCliente_IdOrderByUltimaMensagemEmDesc(cliente.getId())).thenReturn(java.util.List.of(conversa));
        when(mensagemRepository.findFirstByConversa_IdOrderByIdDesc(1L)).thenReturn(Optional.of(mensagem));

        String previa = service.listarConversas(ana).get(0).ultimaMensagem().texto();

        assertThat(previa).isEqualTo("a".repeat(99) + "😀");
    }
}
