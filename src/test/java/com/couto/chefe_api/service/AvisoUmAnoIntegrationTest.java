package com.couto.chefe_api.service;

import com.couto.chefe_api.MenuDeTeste;
import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.Notificacao;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.OcasiaoMenu;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.NotificacaoRepository;
import com.couto.chefe_api.repository.PratosRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Aviso ao chef, uma vez por dia, de que está perto de um ano de uma ocasião especial que ele atendeu (relógio fixo). */
@SpringBootTest
@Import(AvisoUmAnoIntegrationTest.RelogioFixo.class)
@ActiveProfiles("test")
class AvisoUmAnoIntegrationTest {

    /** Fixo e em fevereiro de um ano não bissexto, para o teste de 29/02 (2028) ter "um ano depois" = 28/02/2029. */
    static final LocalDateTime AGORA = LocalDateTime.of(2029, 2, 20, 12, 0);

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        @Primary
        Clock relogioFixo() {
            ZoneId zona = ZoneId.systemDefault();
            return Clock.fixed(AGORA.atZone(zona).toInstant(), zona);
        }
    }

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired PratosRepository pratosRepository;
    @Autowired MenuDeTeste menus;
    @Autowired AvisoUmAnoRotina rotina;
    @MockitoSpyBean AgendamentoService agendamentoService;

    ChefeModel bia;
    UserModel ana;

    @BeforeEach
    void setUp() {
        bia = novoChefe("Bia");
        ana = novoCliente("Ana Souza");
    }

    @AfterEach
    void limparBanco() {
        notificacaoRepository.deleteAll();
        agendamentoRepository.deleteAll();
        pratosRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void dentroDaJanelaDe30DiasNotificaOChef() {
        Agendamento a = concluido(ana, bia, aniversarioEm(10), OcasiaoMenu.ANIVERSARIO);
        Agendamento naBorda = concluido(novoCliente("Caio Lima"), bia, AGORA.plusDays(30), OcasiaoMenu.EMPRESA); // exatamente na borda

        rotina.avisar();

        assertThat(notificacoes()).hasSize(2);
        assertThat(notificacoes()).filteredOn(n -> n.getReferenciaId().equals(ana.getId().toString())).singleElement().satisfies(n -> {
            assertThat(n.getDestinatarioId()).isEqualTo(bia.getId());
            assertThat(n.getMensagem()).isEqualTo("Em 10 dias faz um ano do aniversário que você fez para Ana");
        });
        assertThat(notificacoes()).filteredOn(n -> n.getMensagem().startsWith("Em 30 dias faz um ano do evento de empresa"))
                .hasSize(1);
        assertThat(recarregar(a).getAvisoUmAnoEm()).isEqualTo(AGORA);
        assertThat(recarregar(naBorda).getAvisoUmAnoEm()).isEqualTo(AGORA);
    }

    @Test
    void foraDaJanelaNao() {
        Agendamento longe = concluido(ana, bia, aniversarioEm(31), OcasiaoMenu.ANIVERSARIO);
        Agendamento jaPassou = concluido(novoCliente("Caio"), bia, aniversarioEm(-1), OcasiaoMenu.ANIVERSARIO);

        rotina.avisar();

        assertThat(notificacoes()).isEmpty();
        assertThat(recarregar(longe).getAvisoUmAnoEm()).isNull();
        assertThat(recarregar(jaPassou).getAvisoUmAnoEm()).isNull();
    }

    @Test
    void soAsQuatroOcasioes() {
        concluido(ana, bia, aniversarioEm(5), OcasiaoMenu.FAMILIA);
        concluido(novoCliente("Dani"), bia, aniversarioEm(5), OcasiaoMenu.AMIGOS);
        concluido(novoCliente("Edu"), bia, aniversarioEm(5), OcasiaoMenu.OUTRA);
        concluido(novoCliente("Fabi"), bia, aniversarioEm(5), null);
        concluido(novoCliente("Gabi"), bia, aniversarioEm(5), OcasiaoMenu.JANTAR_ROMANTICO);
        concluido(novoCliente("Hugo"), bia, aniversarioEm(6), OcasiaoMenu.CELEBRACAO);

        rotina.avisar();

        assertThat(notificacoes()).extracting(Notificacao::getMensagem).containsExactlyInAnyOrder(
                "Em 5 dias faz um ano do jantar romântico que você fez para Gabi",
                "Em 6 dias faz um ano da celebração que você fez para Hugo");
    }

    @Test
    void naoAvisaDeAgendamentoQueNaoFoiConcluido() {
        Agendamento confirmado = concluido(ana, bia, aniversarioEm(5), OcasiaoMenu.ANIVERSARIO);
        confirmado.setStatus(StatusAgendamento.CANCELADO);
        agendamentoRepository.save(confirmado);

        rotina.avisar();

        assertThat(notificacoes()).isEmpty();
    }

    @Test
    void envioUnico() {
        concluido(ana, bia, aniversarioEm(10), OcasiaoMenu.ANIVERSARIO);

        rotina.avisar();
        rotina.avisar();

        assertThat(notificacoes()).hasSize(1);
    }

    @Test
    void comAgendamentoFuturoEntreOsDoisNaoEnviaMasGrava() {
        Agendamento antigo = concluido(ana, bia, aniversarioEm(10), OcasiaoMenu.ANIVERSARIO);
        agendamento(ana, bia, AGORA.plusDays(3), StatusAgendamento.CONFIRMADO, OcasiaoMenu.FAMILIA);
        UserModel beto = novoCliente("Beto");
        Agendamento deBeto = concluido(beto, bia, aniversarioEm(10), OcasiaoMenu.ANIVERSARIO);
        agendamento(beto, bia, AGORA.plusDays(3), StatusAgendamento.PENDENTE, OcasiaoMenu.FAMILIA);
        // um agendamento futuro com outro chef, ou já passado e cancelado, não impede
        UserModel cora = novoCliente("Cora");
        Agendamento deCora = concluido(cora, bia, aniversarioEm(10), OcasiaoMenu.ANIVERSARIO);
        agendamento(cora, novoChefe("Outro"), AGORA.plusDays(3), StatusAgendamento.CONFIRMADO, OcasiaoMenu.FAMILIA);
        agendamento(cora, bia, AGORA.plusDays(4), StatusAgendamento.CANCELADO, OcasiaoMenu.FAMILIA);

        rotina.avisar();

        assertThat(notificacoes()).singleElement().satisfies(n ->
                assertThat(n.getReferenciaId()).isEqualTo(cora.getId().toString()));
        assertThat(recarregar(antigo).getAvisoUmAnoEm()).isEqualTo(AGORA);
        assertThat(recarregar(deBeto).getAvisoUmAnoEm()).isEqualTo(AGORA);
        assertThat(recarregar(deCora).getAvisoUmAnoEm()).isEqualTo(AGORA);
    }

    @Test
    void chefInativoOuBloqueadoNaoEnviaMasGrava() {
        ChefeModel inativo = novoChefe("Inativo");
        inativo.setAtivo(false);
        chefeRepository.save(inativo);
        ChefeModel bloqueado = novoChefe("Bloqueado");
        bloqueado.setBloqueadoEm(AGORA.minusDays(1));
        chefeRepository.save(bloqueado);
        Agendamento a = concluido(ana, inativo, aniversarioEm(10), OcasiaoMenu.ANIVERSARIO);
        Agendamento b = concluido(novoCliente("Caio"), bloqueado, aniversarioEm(10), OcasiaoMenu.ANIVERSARIO);

        rotina.avisar();

        assertThat(notificacoes()).isEmpty();
        assertThat(recarregar(a).getAvisoUmAnoEm()).isEqualTo(AGORA);
        assertThat(recarregar(b).getAvisoUmAnoEm()).isEqualTo(AGORA);
    }

    @Test
    void vinteENoveDeFevereiro() {
        // 29/02/2028 + 1 ano = 28/02/2029, 8 dias depois de AGORA
        Agendamento bissexto = agendamento(ana, bia, LocalDateTime.of(2028, 2, 29, 19, 0), StatusAgendamento.CONCLUIDO,
                OcasiaoMenu.ANIVERSARIO);

        rotina.avisar();

        assertThat(notificacoes()).singleElement().satisfies(n ->
                assertThat(n.getMensagem()).isEqualTo("Em 8 dias faz um ano do aniversário que você fez para Ana"));
        assertThat(recarregar(bissexto).getAvisoUmAnoEm()).isEqualTo(AGORA);
    }

    @Test
    void doisAgendamentosDoMesmoParUmAvisoSo() {
        Agendamento perto = concluido(ana, bia, aniversarioEm(7), OcasiaoMenu.ANIVERSARIO);
        Agendamento longe = concluido(ana, bia, aniversarioEm(20), OcasiaoMenu.EMPRESA);

        rotina.avisar();

        assertThat(notificacoes()).singleElement().satisfies(n ->
                assertThat(n.getMensagem()).isEqualTo("Em 7 dias faz um ano do aniversário que você fez para Ana"));
        assertThat(recarregar(perto).getAvisoUmAnoEm()).isEqualTo(AGORA);
        assertThat(recarregar(longe).getAvisoUmAnoEm()).isEqualTo(AGORA);
    }

    @Test
    void textoComUmEZeroDias() {
        concluido(ana, bia, aniversarioEm(1), OcasiaoMenu.ANIVERSARIO);
        concluido(novoCliente("Caio"), bia, aniversarioEm(0).plusHours(2), OcasiaoMenu.JANTAR_ROMANTICO);

        rotina.avisar();

        assertThat(notificacoes()).extracting(Notificacao::getMensagem).containsExactlyInAnyOrder(
                "Em 1 dia faz um ano do aniversário que você fez para Ana",
                "Hoje faz um ano do jantar romântico que você fez para Caio");
    }

    @Test
    void umParQueFalhaNaoImpedeOsDemais() {
        Agendamento quebra = concluido(ana, bia, aniversarioEm(10), OcasiaoMenu.ANIVERSARIO);
        Agendamento segue = concluido(novoCliente("Caio"), bia, aniversarioEm(10), OcasiaoMenu.ANIVERSARIO);
        Mockito.doThrow(new IllegalStateException("falha simulada")).when(agendamentoService)
                .avisarUmAno(List.of(quebra.getId()));

        rotina.avisar();

        assertThat(recarregar(quebra).getAvisoUmAnoEm()).isNull();
        assertThat(recarregar(segue).getAvisoUmAnoEm()).isEqualTo(AGORA);
        assertThat(notificacoes()).hasSize(1);
    }

    // ---------------------------------------------------------------------

    /** Instante, daqui a {@code dias} dias, às 19h ou ao meio-dia do dia 0: o aniversário de uma ocasião de um ano atrás. */
    private static LocalDateTime aniversarioEm(int dias) {
        return AGORA.toLocalDate().plusDays(dias).atTime(dias == 0 ? 13 : 19, 0);
    }

    /** Agendamento concluído cujo aniversário de um ano cai em {@code aniversario}. */
    private Agendamento concluido(UserModel cliente, ChefeModel chefe, LocalDateTime aniversario, OcasiaoMenu ocasiao) {
        return agendamento(cliente, chefe, aniversario.minusYears(1), StatusAgendamento.CONCLUIDO, ocasiao);
    }

    private List<Notificacao> notificacoes() {
        return notificacaoRepository.findAll().stream()
                .filter(n -> n.getEvento() == EventoNotificacao.UM_ANO_DE_CLIENTE).toList();
    }

    private Agendamento recarregar(Agendamento agendamento) {
        return agendamentoRepository.findById(agendamento.getId()).orElseThrow();
    }

    private Agendamento agendamento(UserModel cliente, ChefeModel chefe, LocalDateTime inicio, StatusAgendamento status,
                                    OcasiaoMenu ocasiao) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(novoEndereco(cliente));
        agendamento.setChefe(chefe);
        agendamento.setStatus(status);
        agendamento.setOcasiao(ocasiao);
        agendamento.setDataHoraInicial(inicio);
        agendamento.aplicarMenu(menus.ativo(chefe), 2);
        return agendamentoRepository.save(agendamento);
    }

    private EnderecoModel novoEndereco(UserModel dono) {
        EnderecoModel endereco = new EnderecoModel();
        endereco.setRua("Rua A");
        endereco.setNumero("1");
        endereco.setBairro("Centro");
        endereco.setCidade("São Paulo");
        endereco.setEstado("SP");
        endereco.setCep("01001-000");
        endereco.setTipoDeResidencia(TipoDeResidencia.CASA);
        endereco.setUsuario(dono);
        return enderecoRepository.save(endereco);
    }

    private UserModel novoCliente(String nome) {
        UserModel usuario = new UserModel();
        usuario.setNome(nome);
        usuario.setEmail(nome.toLowerCase().replace(' ', '.') + "-" + UUID.randomUUID() + "@x.com");
        usuario.setEndereco(new ArrayList<>());
        return usuarioRepository.save(TelefoneDeTeste.verificado(usuario));
    }

    private ChefeModel novoChefe(String nome) {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome(nome);
        chefe.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setEspecialidades(Set.of(Especialidades.BRASILEIRA));
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        return chefeRepository.save(TelefoneDeTeste.verificado(chefe));
    }
}
