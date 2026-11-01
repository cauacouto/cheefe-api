package com.couto.chefe_api.service;

import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.Avaliacao;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.dto.AvaliarRequestDto;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.exception.AvaliacaoForaDoPrazoException;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.AvaliacaoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Prazo exato e ordem filtro → trava, com relógio fixo (o resto está no AvaliacaoIntegrationTest). */
class AvaliacaoServiceTest {

    static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    static final LocalDateTime AGORA = LocalDateTime.now(RELOGIO);

    final AgendamentoRepository agendamentoRepository = mock(AgendamentoRepository.class);
    final AvaliacaoRepository avaliacaoRepository = mock(AvaliacaoRepository.class);
    final ChefeRepository chefeRepository = mock(ChefeRepository.class);
    final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    final ModeracaoConteudoService moderacao = mock(ModeracaoConteudoService.class);
    final NotificacaoService notificacaoService = mock(NotificacaoService.class);
    final AvaliacaoService service = new AvaliacaoService(agendamentoRepository, avaliacaoRepository, chefeRepository,
            usuarioRepository, moderacao, notificacaoService, RELOGIO);

    final UserModel ana = new UserModel();
    final ChefeModel bia = new ChefeModel();
    final Agendamento agendamento = new Agendamento();
    UsuarioAutenticado anaLogada;

    @BeforeEach
    void setUp() {
        ana.setId(UUID.randomUUID());
        ana.setNome("Ana");
        bia.setId(UUID.randomUUID());
        bia.setNome("Bia");
        anaLogada = new UsuarioAutenticado(ana.getId(), "ana@x.com", TipoUsuario.CLIENTE);
        agendamento.setId(1L);
        agendamento.setUsuario(ana);
        agendamento.setChefes(new ArrayList<>(List.of(bia)));
        agendamento.setStatus(StatusAgendamento.CONCLUIDO);
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento));
        when(chefeRepository.somarAvaliacao(any(), anyInt())).thenReturn(1);
        when(moderacao.verificar(any(), any())).thenAnswer(inv -> new ConteudoVerificado(inv.getArgument(0), false));
        when(avaliacaoRepository.save(any())).thenAnswer(inv -> {
            Avaliacao a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });
    }

    @Test
    void prazoExatoDe7DiasPassa() {
        agendamento.setConcluidoEm(AGORA.minusDays(7));

        service.avaliar(1L, anaLogada, pedido("bom"));

        verify(chefeRepository).somarAvaliacao(bia.getId(), 5);
    }

    @Test
    void seteDiasEUmMinutoDa409() {
        agendamento.setConcluidoEm(AGORA.minusDays(7).minusMinutes(1));

        assertThatThrownBy(() -> service.avaliar(1L, anaLogada, pedido("bom")))
                .isInstanceOf(AvaliacaoForaDoPrazoException.class)
                .hasMessage("O prazo para avaliar (7 dias depois da conclusão) já passou");
        verify(chefeRepository, never()).somarAvaliacao(any(), anyInt());
    }

    @Test
    void verificaOTextoAntesDeEscreverNoAvaliado() {
        agendamento.setConcluidoEm(AGORA.minusDays(1));

        service.avaliar(1L, anaLogada, pedido("bom"));

        // o bloqueio do autor (transação própria) não pode esperar um lock de escrita desta transação
        InOrder ordem = inOrder(moderacao, chefeRepository);
        ordem.verify(moderacao).verificar("bom", anaLogada);
        ordem.verify(chefeRepository).somarAvaliacao(bia.getId(), 5);
    }

    private AvaliarRequestDto pedido(String comentario) {
        return new AvaliarRequestDto(bia.getId(), 5, List.of(), comentario, null);
    }
}
