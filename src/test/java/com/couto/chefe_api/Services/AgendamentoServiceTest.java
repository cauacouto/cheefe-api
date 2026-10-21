package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.AgendamentoResquestDto;
import com.couto.chefe_api.Excepitons.AgendamentoInvalidoException;
import com.couto.chefe_api.Excepitons.AgendamentoNaoEditavelException;
import com.couto.chefe_api.Excepitons.ChefeIndisponivelException;
import com.couto.chefe_api.Excepitons.ChefeNaoEncontradoException;
import com.couto.chefe_api.Mapper.AgendamentoMapper;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.repositorys.AgendamentoRepository;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.EndercoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendamentoServiceTest {

    private static final String EMAIL = "ana@x.com";
    private static final LocalDateTime DIA = LocalDateTime.now().plusDays(10).withHour(0).withMinute(0).withSecond(0).withNano(0);

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ChefeRepository chefeRepository;
    @Mock
    private AgendamentoMapper agendamentoMapper;
    @Mock
    private EndercoRepository endercoRepository;
    @Mock
    private AgendamentoRepository agendamentoRepository;

    @InjectMocks
    private agendamentoService service;

    private final UserModel usuario = new UserModel();
    private final EnderecoModel endereco = new EnderecoModel();
    private final ChefeModel chefeA = chefe("100.00");
    private final ChefeModel chefeB = chefe("50.50");

    @BeforeEach
    void setUp() {
        endereco.setId(1L);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(endercoRepository.findById(1L)).thenReturn(Optional.of(endereco));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    class Criar {

        @Test
        void periodoValidoCalculaValorPelaSomaDosChefes() {
            chefesExistentes(chefeA, chefeB);

            service.criarAgendamento(dto(DIA.withHour(18), DIA.withHour(21), 1L, ids(chefeA, chefeB), 4), EMAIL);

            Agendamento salvo = agendamentoSalvo();
            assertThat(salvo.getValorTotal()).isEqualByComparingTo("451.50");
            assertThat(salvo.getChefes()).containsExactlyInAnyOrder(chefeA, chefeB);
            assertThat(salvo.getEndereco()).isSameAs(endereco);
            assertThat(salvo.getUsuario()).isSameAs(usuario);
        }

        @Test
        void fimAntesDoInicioEhRejeitado() {
            chefesExistentes(chefeA);

            assertThatThrownBy(() -> service.criarAgendamento(
                    dto(DIA.withHour(21), DIA.withHour(18), 1L, ids(chefeA), 2), EMAIL))
                    .isInstanceOf(AgendamentoInvalidoException.class);
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        void fimIgualAoInicioEhRejeitado() {
            chefesExistentes(chefeA);

            assertThatThrownBy(() -> service.criarAgendamento(
                    dto(DIA.withHour(18), DIA.withHour(18), 1L, ids(chefeA), 2), EMAIL))
                    .isInstanceOf(AgendamentoInvalidoException.class);
        }

        @Test
        void chefeInexistenteEhRejeitado() {
            chefesExistentes(chefeA);

            assertThatThrownBy(() -> service.criarAgendamento(
                    dto(DIA.withHour(18), DIA.withHour(21), 1L, List.of(chefeA.getId(), UUID.randomUUID()), 2), EMAIL))
                    .isInstanceOf(ChefeNaoEncontradoException.class);
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        void semChefesEhRejeitado() {
            assertThatThrownBy(() -> service.criarAgendamento(
                    dto(DIA.withHour(18), DIA.withHour(21), 1L, null, 2), EMAIL))
                    .isInstanceOf(AgendamentoInvalidoException.class);
            assertThatThrownBy(() -> service.criarAgendamento(
                    dto(DIA.withHour(18), DIA.withHour(21), 1L, List.of(), 2), EMAIL))
                    .isInstanceOf(AgendamentoInvalidoException.class);
        }

        @Test
        void chefeInativoOuIndisponivelEhRejeitado() {
            chefeB.setAtivo(false);
            chefesExistentes(chefeA, chefeB);

            assertThatThrownBy(() -> service.criarAgendamento(
                    dto(DIA.withHour(18), DIA.withHour(21), 1L, ids(chefeA, chefeB), 2), EMAIL))
                    .isInstanceOf(ChefeIndisponivelException.class);
        }

        @Test
        void idsRepetidosContamUmaVez() {
            chefesExistentes(chefeA);

            service.criarAgendamento(
                    dto(DIA.withHour(18), DIA.withHour(20), 1L, List.of(chefeA.getId(), chefeA.getId()), 2), EMAIL);

            assertThat(agendamentoSalvo().getValorTotal()).isEqualByComparingTo("200.00");
        }
    }

    @Nested
    class Atualizar {

        private Agendamento agenda;

        @BeforeEach
        void agendaExistente() {
            agenda = new Agendamento();
            agenda.setId(7L);
            agenda.setEndereco(endereco);
            agenda.setChefes(new ArrayList<>(List.of(chefeA)));
            agenda.setDataHoraInicial(DIA.withHour(18));
            agenda.setDataHoraFinal(DIA.withHour(20));
            agenda.setQuantidadePessoas(2);
            agenda.setValorTotal(new BigDecimal("200.00"));
            when(agendamentoRepository.findById(7L)).thenReturn(Optional.of(agenda));
        }

        @Test
        void semEnderecoIdMantemEnderecoSemBuscar() {
            service.atualizarAgedamento(dto(null, null, null, null, 5), 7L);

            assertThat(agendamentoSalvo().getEndereco()).isSameAs(endereco);
            verify(endercoRepository, never()).findById(any());
        }

        @Test
        void atualizaQuantidadeDePessoas() {
            service.atualizarAgedamento(dto(null, null, null, null, 5), 7L);

            assertThat(agendamentoSalvo().getQuantidadePessoas()).isEqualTo(5);
        }

        @Test
        void listaDeChefesPreenchidaTrocaChefesERecalculaValor() {
            chefesExistentes(chefeB);

            service.atualizarAgedamento(dto(null, null, null, ids(chefeB), null), 7L);

            Agendamento salvo = agendamentoSalvo();
            assertThat(salvo.getChefes()).containsExactly(chefeB);
            assertThat(salvo.getValorTotal()).isEqualByComparingTo("101.00");
        }

        @Test
        void listaDeChefesVaziaNaoRemoveOsChefes() {
            service.atualizarAgedamento(dto(null, null, null, List.of(), null), 7L);

            assertThat(agendamentoSalvo().getChefes()).containsExactly(chefeA);
            verify(chefeRepository, never()).findAllById(anyIterable());
        }

        @Test
        void chefeInexistenteNaAtualizacaoEhRejeitado() {
            chefesExistentes();

            assertThatThrownBy(() -> service.atualizarAgedamento(
                    dto(null, null, null, List.of(UUID.randomUUID()), null), 7L))
                    .isInstanceOf(ChefeNaoEncontradoException.class);
            assertThat(agenda.getChefes()).containsExactly(chefeA);
        }

        @Test
        void novasDatasSaoGravadasNaEntidadeERecalculamValor() {
            service.atualizarAgedamento(dto(DIA.withHour(19), DIA.withHour(22), null, null, null), 7L);

            Agendamento salvo = agendamentoSalvo();
            assertThat(salvo.getDataHoraInicial()).isEqualTo(DIA.withHour(19));
            assertThat(salvo.getDataHoraFinal()).isEqualTo(DIA.withHour(22));
            assertThat(salvo.getValorTotal()).isEqualByComparingTo("300.00");
        }

        @Test
        void soODataFinalUsaOInicioExistente() {
            service.atualizarAgedamento(dto(null, DIA.withHour(21), null, null, null), 7L);

            Agendamento salvo = agendamentoSalvo();
            assertThat(salvo.getDataHoraInicial()).isEqualTo(DIA.withHour(18));
            assertThat(salvo.getValorTotal()).isEqualByComparingTo("300.00");
        }

        @Test
        void fimAntesDoInicioExistenteEhRejeitado() {
            assertThatThrownBy(() -> service.atualizarAgedamento(
                    dto(null, DIA.withHour(17), null, null, null), 7L))
                    .isInstanceOf(AgendamentoInvalidoException.class);
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        void agendamentoForaDePendenteNaoPodeSerEditado() {
            agenda.setStatus(StatusAgendamento.CONFIRMADO);

            assertThatThrownBy(() -> service.atualizarAgedamento(dto(null, null, null, null, 5), 7L))
                    .isInstanceOf(AgendamentoNaoEditavelException.class);
            verify(agendamentoRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------

    private static ChefeModel chefe(String valorHora) {
        ChefeModel chefe = new ChefeModel();
        chefe.setId(UUID.randomUUID());
        chefe.setNome("chefe " + valorHora);
        chefe.setValorHora(new BigDecimal(valorHora));
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        return chefe;
    }

    private void chefesExistentes(ChefeModel... chefes) {
        when(chefeRepository.findAllById(anyIterable())).thenReturn(new ArrayList<>(List.of(chefes)));
    }

    private static List<UUID> ids(ChefeModel... chefes) {
        return java.util.Arrays.stream(chefes).map(ChefeModel::getId).toList();
    }

    private static AgendamentoResquestDto dto(LocalDateTime inicio, LocalDateTime fim, Long enderecoId,
                                              List<UUID> chefeIds, Integer quantidadePessoas) {
        return new AgendamentoResquestDto(inicio, fim, enderecoId, chefeIds, quantidadePessoas);
    }

    private Agendamento agendamentoSalvo() {
        ArgumentCaptor<Agendamento> captor = ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository).save(captor.capture());
        return captor.getValue();
    }
}
