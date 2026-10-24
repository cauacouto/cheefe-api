package com.couto.chefe_api.service;

import com.couto.chefe_api.dto.MensagemDto;
import com.couto.chefe_api.exception.ChefeIndisponivelException;
import com.couto.chefe_api.exception.ConflitoException;
import com.couto.chefe_api.exception.ContaInativaException;
import com.couto.chefe_api.exception.RecursoNaoEncontradoException;
import com.couto.chefe_api.exception.RegraDeNegocioException;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.service.eventos.MensagemApagadaEvent;
import com.couto.chefe_api.service.eventos.MensagemEditadaEvent;
import com.couto.chefe_api.service.eventos.MensagemEnviadaEvent;
import com.couto.chefe_api.service.eventos.MensagensLidasEvent;
import org.mockito.ArgumentCaptor;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Conversa;
import com.couto.chefe_api.domain.Mensagem;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.ConversaRepository;
import com.couto.chefe_api.repository.MensagemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final ChefeRepository chefeRepository = mock(ChefeRepository.class);
    private final ModeracaoConteudoService moderacao = mock(ModeracaoConteudoService.class);
    private final ChatService service = new ChatService(conversaRepository, mensagemRepository,
            usuarioRepository, chefeRepository, mock(NotificacaoService.class), moderacao,
            mock(ExigenciaTelefone.class), eventPublisher, RELOGIO,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

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
        when(conversaRepository.travarPorId(1L)).thenReturn(Optional.of(conversa));
        when(mensagemRepository.findByIdAndConversa_Id(10L, 1L)).thenReturn(Optional.of(mensagem));
        when(moderacao.verificar(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void verificaOTextoAntesDeTravarAConversa() {
        when(mensagemRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        service.enviar(1L, ana, " oi ");

        // o bloqueio do autor roda numa transação própria e não pode esperar uma trava desta
        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(moderacao, conversaRepository);
        ordem.verify(moderacao).verificar("oi", ana);
        ordem.verify(conversaRepository).travarPorId(1L);
    }

    @Test
    void conteudoBloqueadoNaoGravaNemTrava() {
        when(moderacao.verificar("seu odiento", ana)).thenThrow(new com.couto.chefe_api.exception.ConteudoBloqueadoException());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.enviar(1L, ana, "seu odiento"))
                .isInstanceOf(com.couto.chefe_api.exception.ConteudoBloqueadoException.class);
        verify(conversaRepository, never()).travarPorId(1L);
        verify(mensagemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void editarVerificaOTextoAntesDeGravar() {
        java.util.List<String> textoNaVerificacao = new java.util.ArrayList<>();
        when(moderacao.verificar(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    textoNaVerificacao.add(mensagem.getTexto());
                    return inv.getArgument(0);
                });

        service.editar(1L, 10L, ana, "corrigido");

        // quando o filtro roda, o texto novo ainda não foi para a mensagem
        assertThat(textoNaVerificacao).containsExactly("original");
        assertThat(mensagem.getTexto()).isEqualTo("corrigido");
    }

    @Test
    void editarComConteudoBloqueadoNaoGravaNemPublica() {
        when(moderacao.verificar("seu odiento", ana)).thenThrow(new com.couto.chefe_api.exception.ConteudoBloqueadoException());

        assertThatThrownBy(() -> service.editar(1L, 10L, ana, "seu odiento"))
                .isInstanceOf(com.couto.chefe_api.exception.ConteudoBloqueadoException.class);

        assertThat(mensagem.getTexto()).isEqualTo("original");
        assertThat(mensagem.getEditadaEm()).isNull();
        verify(eventPublisher, never()).publishEvent(any(Object.class));
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
    void enviarParaChefBloqueadoEhRecusado() {
        chefe.setBloqueadoEm(java.time.LocalDateTime.now());

        assertThatThrownBy(() -> service.enviar(1L, ana, "oi")).isInstanceOf(ChefeIndisponivelException.class);
        verify(mensagemRepository, never()).save(any());
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

        verify(conversaRepository).travarPorId(1L);
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
        when(conversaRepository.findByCliente_IdOrderByUltimaMensagemEmDescIdDesc(any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(conversa)));
        when(mensagemRepository.ultimasDasConversas(java.util.List.of(1L))).thenReturn(java.util.List.of(mensagem));

        String previa = service.listarConversas(ana, org.springframework.data.domain.PageRequest.of(0, 20))
                .getContent().get(0).ultimaMensagem().texto();

        assertThat(previa).isEqualTo("a".repeat(99) + "😀");
    }

    @Test
    void chefApagadoNaoEditaApagaNemMarcaLidas() {
        mensagem.setRemetenteId(chefe.getId());
        mensagem.setRemetenteTipo(TipoUsuario.CHEFE);
        chefe.setAtivo(false);

        assertThatThrownBy(() -> service.editar(1L, 10L, bia, "x")).isInstanceOf(ContaInativaException.class);
        assertThatThrownBy(() -> service.apagar(1L, 10L, bia)).isInstanceOf(ContaInativaException.class);
        assertThatThrownBy(() -> service.marcarLidas(1L, bia)).isInstanceOf(ContaInativaException.class);
        assertThat(mensagem.isApagada()).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void clienteAindaApagaAPropriaMensagemComChefApagado() {
        chefe.setAtivo(false);

        service.apagar(1L, 10L, ana);

        assertThat(mensagem.isApagada()).isTrue();
    }

    @Test
    void conversaNovaUsaORelogio() {
        chefe.setAtivo(true);
        when(chefeRepository.findById(chefe.getId())).thenReturn(Optional.of(chefe));
        when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
        when(conversaRepository.findByCliente_IdAndChefe_Id(cliente.getId(), chefe.getId())).thenReturn(Optional.empty());
        when(conversaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.abrirConversa(cliente.getId(), chefe.getId());

        ArgumentCaptor<Conversa> captor = ArgumentCaptor.forClass(Conversa.class);
        verify(conversaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCriadaEm()).isEqualTo(AGORA);
        assertThat(captor.getValue().getUltimaMensagemEm()).isEqualTo(AGORA);
    }
}
