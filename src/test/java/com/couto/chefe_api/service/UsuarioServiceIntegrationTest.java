package com.couto.chefe_api.service;

import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.Avaliacao;
import com.couto.chefe_api.domain.BloqueioConta;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.CategoriaConteudo;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.AvaliacaoRepository;
import com.couto.chefe_api.repository.BloqueioContaRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.NotificacaoRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.dto.EnderecoRequestDto;
import com.couto.chefe_api.dto.UserRequestDto;
import com.couto.chefe_api.dto.UserResponseDto;
import com.couto.chefe_api.exception.EnderecoInvalidoException;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.repository.EnderecoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A4: o PUT de usuário falhava com UnsupportedOperationException (lista imutável
 * de Stream.toList()) e, sem orphanRemoval, acumularia endereços.
 */
@SpringBootTest
@ActiveProfiles("test")
class UsuarioServiceIntegrationTest {

    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EnderecoRepository enderecoRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private AvaliacaoRepository avaliacaoRepository;
    @Autowired
    private BloqueioContaRepository bloqueioContaRepository;
    @Autowired
    private ChefeRepository chefeRepository;
    @Autowired
    private NotificacaoRepository notificacaoRepository;
    @Autowired
    private NotificacaoService notificacaoService;

    private UUID usuarioId;

    @BeforeEach
    void criarUsuarioComUmEndereco() {
        UserModel usuario = new UserModel();
        usuario.setNome("Ana");
        usuario.setEmail("ana-" + UUID.randomUUID() + "@x.com");
        usuario.setTelefone("11999999999");
        usuario.setEndereco(new ArrayList<>());

        EnderecoModel antigo = new EnderecoModel();
        antigo.setRua("Rua Antiga");
        antigo.setNumero("1");
        antigo.setTipoDeResidencia(TipoDeResidencia.CASA);
        antigo.setUsuario(usuario);
        usuario.getEndereco().add(antigo);

        usuarioId = usuarioRepository.save(usuario).getId();
    }

    @AfterEach
    void limpar() {
        notificacaoRepository.deleteAll();
        bloqueioContaRepository.deleteAll();
        agendamentoRepository.deleteAll();
        chefeRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void substituiOsEnderecosSemAcumular() {
        UserRequestDto dto = new UserRequestDto("Ana Maria", "11888888888",
                List.of(endereco("Rua Nova", TipoDeResidencia.CASA, null),
                        endereco("Rua do Prédio", TipoDeResidencia.APARTAMENTO, "Bloco B ap 12")));

        UserResponseDto resposta = usuarioService.atualizar(dto, usuarioId);

        assertThat(resposta.getNome()).isEqualTo("Ana Maria");
        assertThat(resposta.getEndereco()).extracting("rua").containsExactlyInAnyOrder("Rua Nova", "Rua do Prédio");
        assertThat(enderecosDoUsuario()).extracting(EnderecoModel::getRua)
                .containsExactlyInAnyOrder("Rua Nova", "Rua do Prédio");
    }

    @Test
    void funcionaComEntidadeGerenciadaComoNoOpenSessionInView() {
        UserRequestDto dto = new UserRequestDto("Ana Maria", "11888888888",
                List.of(endereco("Rua Nova", TipoDeResidencia.CASA, null)));

        transactionTemplate.executeWithoutResult(status -> {
            usuarioRepository.findById(usuarioId).orElseThrow();
            usuarioService.atualizar(dto, usuarioId);
        });

        assertThat(enderecosDoUsuario()).extracting(EnderecoModel::getRua).containsExactly("Rua Nova");
    }

    @Test
    void enderecoInvalidoNaoAlteraNada() {
        UserRequestDto dto = new UserRequestDto("Outro nome", "11",
                List.of(endereco("Rua do Prédio", TipoDeResidencia.APARTAMENTO, " ")));

        assertThatThrownBy(() -> usuarioService.atualizar(dto, usuarioId))
                .isInstanceOf(EnderecoInvalidoException.class);

        assertThat(usuarioRepository.findById(usuarioId)).get().extracting(UserModel::getNome).isEqualTo("Ana");
        assertThat(enderecosDoUsuario()).extracting(EnderecoModel::getRua).containsExactly("Rua Antiga");
    }

    private List<EnderecoModel> enderecosDoUsuario() {
        return enderecoRepository.findAll().stream()
                .filter(e -> e.getUsuario() != null && usuarioId.equals(e.getUsuario().getId()))
                .toList();
    }

    private static EnderecoRequestDto endereco(String rua, TipoDeResidencia tipo, String complemento) {
        return new EnderecoRequestDto(rua, "10", complemento, "01001-000", "São Paulo", "Centro", "SP", tipo);
    }

    @Test
    void atualizarNaoMudaOEmail() {
        String emailOriginal = usuarioRepository.findById(usuarioId).orElseThrow().getEmail();
        UserRequestDto dto = new UserRequestDto("Ana Maria", "11888888888", List.of(
                new EnderecoRequestDto("Rua Nova", "2", null, "01001000", "São Paulo", "Sé", "SP", TipoDeResidencia.CASA)));

        UserResponseDto resposta = usuarioService.atualizar(dto, usuarioId);

        assertThat(resposta.getEmail()).isEqualTo(emailOriginal);
        assertThat(usuarioRepository.findById(usuarioId).orElseThrow().getEmail()).isEqualTo(emailOriginal);
    }

    @Test
    void apagarContaApagaNotificacaoDeBloqueioDosModeradores() {
        BloqueioConta doCliente = bloqueio(usuarioId);
        BloqueioConta deOutraConta = bloqueio(UUID.randomUUID());
        UUID moderador1 = UUID.randomUUID();
        UUID moderador2 = UUID.randomUUID();
        for (UUID moderador : List.of(moderador1, moderador2)) {
            notificacaoService.notificar(moderador, TipoUsuario.MODERADOR, EventoNotificacao.CONTA_BLOQUEADA,
                    "bloqueada", String.valueOf(doCliente.getId()));
        }
        notificacaoService.notificar(moderador1, TipoUsuario.MODERADOR, EventoNotificacao.CONTA_BLOQUEADA,
                "outra", String.valueOf(deOutraConta.getId()));

        usuarioService.deletar(usuarioId);

        // só as notificações deste teste: outras classes dividem o banco e podem ter deixado as delas
        assertThat(notificacaoRepository.findAll())
                .filteredOn(n -> List.of(moderador1, moderador2).contains(n.getDestinatarioId()))
                .extracting(n -> n.getReferenciaId())
                .containsExactly(String.valueOf(deOutraConta.getId()));
    }

    @Test
    void apagarContaDeQuemAvaliouDoisChefsDescontaOsDoisEApagaAsNotificacoes() {
        ChefeModel bia = novoChefe("Bia");
        ChefeModel caio = novoChefe("Caio");
        UserModel cliente = usuarioRepository.findById(usuarioId).orElseThrow();
        Agendamento ag = new Agendamento();
        ag.setUsuario(cliente);
        ag.setChefes(new ArrayList<>(List.of(bia, caio)));
        ag.setDataHoraInicial(LocalDateTime.now().minusDays(2));
        ag.setDataHoraFinal(LocalDateTime.now().minusDays(2).plusHours(2));
        ag.setQuantidadePessoas(2);
        ag.setValorTotal(new BigDecimal("200.00"));
        ag.setStatus(StatusAgendamento.CONCLUIDO);
        ag.setConcluidoEm(LocalDateTime.now().minusDays(1));
        ag = agendamentoRepository.save(ag);
        // outra pessoa também avaliou a Bia: só a nota do cliente apagado sai
        transactionTemplate.executeWithoutResult(t -> chefeRepository.somarAvaliacao(bia.getId(), 2));
        for (ChefeModel chefe : List.of(bia, caio)) {
            transactionTemplate.executeWithoutResult(t -> chefeRepository.somarAvaliacao(chefe.getId(), 5));
            Avaliacao a = new Avaliacao();
            a.setAgendamento(ag);
            a.setAutorId(usuarioId);
            a.setAutorTipo(TipoUsuario.CLIENTE);
            a.setAvaliadoId(chefe.getId());
            a.setAvaliadoTipo(TipoUsuario.CHEFE);
            a.setNota(5);
            a.setComentario("ótimo");
            a.setPublicadoEm(LocalDateTime.now());
            a = avaliacaoRepository.save(a);
            notificacaoService.notificar(usuarioId, TipoUsuario.CLIENTE, EventoNotificacao.AVALIACAO_RESPONDIDA,
                    "respondeu", String.valueOf(a.getId()));
        }
        assertThat(notificacaoRepository.count()).isEqualTo(2);

        usuarioService.deletar(usuarioId);

        ChefeModel biaDepois = chefeRepository.findById(bia.getId()).orElseThrow();
        ChefeModel caioDepois = chefeRepository.findById(caio.getId()).orElseThrow();
        assertThat(biaDepois.getTotalAvaliacoes()).isEqualTo(1);
        assertThat(biaDepois.getSomaNotas()).isEqualTo(2);
        assertThat(caioDepois.getTotalAvaliacoes()).isZero();
        assertThat(caioDepois.getSomaNotas()).isZero();
        assertThat(avaliacaoRepository.count()).isZero();
        assertThat(notificacaoRepository.findAll()).isEmpty();
    }

    private BloqueioConta bloqueio(UUID contaId) {
        BloqueioConta b = new BloqueioConta();
        b.setContaId(contaId);
        b.setContaTipo(TipoUsuario.CLIENTE);
        b.setCategoria(CategoriaConteudo.ODIO);
        b.setTermo("odiento");
        b.setTexto("texto");
        b.setCriadoEm(LocalDateTime.now());
        return bloqueioContaRepository.save(b);
    }

    private ChefeModel novoChefe(String nome) {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome(nome);
        chefe.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("100"));
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        return chefeRepository.save(chefe);
    }
}
