package com.couto.chefe_api.service;

import com.couto.chefe_api.domain.BloqueioConta;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Notificacao;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusBloqueio;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.exception.ConteudoBloqueadoException;
import com.couto.chefe_api.repository.BloqueioContaRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.NotificacaoRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.security.ModeracaoRegistry;
import com.couto.chefe_api.security.UsuarioAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.couto.chefe_api.FiltroDeTeste.class)
class ModeracaoConteudoServiceIntegrationTest {

    @Autowired
    private ModeracaoConteudoService service;
    @Autowired
    private TransactionTemplate transacao;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ChefeRepository chefeRepository;
    @Autowired
    private BloqueioContaRepository bloqueioRepository;
    @Autowired
    private NotificacaoRepository notificacaoRepository;
    @Autowired
    private ModeracaoRegistry moderacaoRegistry;
    @MockitoSpyBean
    private SseRegistry sseRegistry;

    @AfterEach
    void limpar() {
        notificacaoRepository.deleteAll(notificacaoRepository.findAll().stream()
                .filter(n -> n.getEvento() == EventoNotificacao.CONTA_BLOQUEADA).toList());
        bloqueioRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void ofensaSoMascara() {
        UserModel cliente = cliente();

        ConteudoVerificado verificado = service.verificar("que palavrao", autenticado(cliente.getId(), TipoUsuario.CLIENTE));

        assertThat(verificado.texto()).isEqualTo("que ***");
        assertThat(verificado.contatoOculto()).isFalse();
        assertThat(usuarioRepository.findById(cliente.getId()).orElseThrow().getBloqueadoEm()).isNull();
        assertThat(bloqueioRepository.count()).isZero();
    }

    @Test
    void ofensaEContatoNaMesmaMensagemViramDoisAsteriscos() {
        UserModel cliente = cliente();

        ConteudoVerificado verificado = service.verificar("um palavrao, me liga 21 98888-7777",
                autenticado(cliente.getId(), TipoUsuario.CLIENTE));

        assertThat(verificado.texto()).isEqualTo("um ***, me liga ***");
        assertThat(verificado.contatoOculto()).isTrue();
        assertThat(usuarioRepository.findById(cliente.getId()).orElseThrow().getBloqueadoEm()).isNull();
    }

    @Test
    void odioBloqueiaAntesDaMascaraDeContato() {
        UserModel cliente = cliente();
        var autor = autenticado(cliente.getId(), TipoUsuario.CLIENTE);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.verificar("seu odiento 21 98888-7777", autor))
                .isInstanceOf(com.couto.chefe_api.exception.ConteudoBloqueadoException.class);

        assertThat(usuarioRepository.findById(cliente.getId()).orElseThrow().getBloqueadoEm()).isNotNull();
        // a fila da moderação vê o texto como foi escrito
        assertThat(bloqueioRepository.findAll()).singleElement()
                .satisfies(b -> assertThat(b.getTexto()).isEqualTo("seu odiento 21 98888-7777"));
    }

    @Test
    void contatoSozinhoNaoBloqueiaNinguem() {
        UserModel cliente = cliente();

        ConteudoVerificado verificado = service.verificar("chef@gmail.com", autenticado(cliente.getId(), TipoUsuario.CLIENTE));

        assertThat(verificado.texto()).isEqualTo("***");
        assertThat(bloqueioRepository.count()).isZero();
    }

    @Test
    void odioBloqueiaMesmoComARequisicaoFalhando() {
        UserModel cliente = cliente();
        UsuarioAutenticado autor = autenticado(cliente.getId(), TipoUsuario.CLIENTE);

        // a exceção sai da transação do chamador, que sofre rollback; o bloqueio já foi commitado
        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> service.verificar("seu odiento", autor)))
                .isInstanceOf(ConteudoBloqueadoException.class)
                .hasMessage("Conteúdo bloqueado e conta suspensa para análise da moderação");

        UserModel salvo = usuarioRepository.findById(cliente.getId()).orElseThrow();
        assertThat(salvo.getBloqueadoEm()).isNotNull();
        assertThat(salvo.getBloqueioMotivo()).isEqualTo("ODIO");
        List<BloqueioConta> bloqueios = bloqueioRepository.findAll();
        assertThat(bloqueios).hasSize(1).allSatisfy(b -> {
            assertThat(b.getStatus()).isEqualTo(StatusBloqueio.ABERTO);
            assertThat(b.getTermo()).isEqualTo("odiento");
            assertThat(b.getContaId()).isEqualTo(cliente.getId());
        });
        assertThat(bloqueios).extracting(BloqueioConta::getTexto).contains("seu odiento");
    }

    @Test
    void conteudoBloqueadoDevolve403() {
        UserModel cliente = cliente();

        assertThatThrownBy(() -> service.verificar("vou te pegar", autenticado(cliente.getId(), TipoUsuario.CLIENTE)))
                .isInstanceOfSatisfying(ConteudoBloqueadoException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(403));
    }

    @Test
    void moderadoresSaoAvisados() {
        UserModel cliente = cliente();

        assertThatThrownBy(() -> service.verificar("seu odiento", autenticado(cliente.getId(), TipoUsuario.CLIENTE)))
                .isInstanceOf(ConteudoBloqueadoException.class);

        Long bloqueioId = bloqueioRepository.findAll().get(0).getId();
        List<Notificacao> avisos = notificacaoRepository.findAll().stream()
                .filter(n -> n.getEvento() == EventoNotificacao.CONTA_BLOQUEADA).toList();
        assertThat(avisos).hasSize(moderacaoRegistry.ids().size()).isNotEmpty();
        assertThat(avisos).allSatisfy(n -> {
            assertThat(moderacaoRegistry.ids()).contains(n.getDestinatarioId());
            assertThat(n.getMensagem()).isEqualTo("Conta bloqueada automaticamente (ODIO) — revisar");
            assertThat(n.getReferenciaId()).isEqualTo(String.valueOf(bloqueioId));
        });
    }

    @Test
    void chefTambemEhBloqueado() {
        ChefeModel chefe = chefe();

        assertThatThrownBy(() -> service.verificar("vou te pegar", autenticado(chefe.getId(), TipoUsuario.CHEFE)))
                .isInstanceOf(ConteudoBloqueadoException.class);

        ChefeModel salvo = chefeRepository.findById(chefe.getId()).orElseThrow();
        assertThat(salvo.getBloqueadoEm()).isNotNull();
        assertThat(salvo.getBloqueioMotivo()).isEqualTo("AMEACA");
        assertThat(bloqueioRepository.findAll()).singleElement()
                .satisfies(b -> assertThat(b.getContaTipo()).isEqualTo(TipoUsuario.CHEFE));
    }

    @Test
    void textoLongoEhTruncadoSemFalhar() {
        UserModel cliente = cliente();
        String longo = "odiento " + "x".repeat(3000);

        assertThatThrownBy(() -> service.verificar(longo, autenticado(cliente.getId(), TipoUsuario.CLIENTE)))
                .isInstanceOf(ConteudoBloqueadoException.class);

        assertThat(bloqueioRepository.findAll()).singleElement()
                .satisfies(b -> assertThat(b.getTexto()).hasSize(2000));
    }

    @Test
    void bloqueioEncerraOsStreamsDaContaDepoisDoCommit() {
        UserModel cliente = cliente();

        assertThatThrownBy(() -> service.verificar("seu odiento", autenticado(cliente.getId(), TipoUsuario.CLIENTE)))
                .isInstanceOf(ConteudoBloqueadoException.class);

        verify(sseRegistry).encerrar(cliente.getId());
    }

    @Test
    void falhaAoEncerrarStreamsNaoQuebraOBloqueio() {
        UserModel cliente = cliente();
        doThrow(new IllegalStateException("sse")).when(sseRegistry).encerrar(cliente.getId());

        assertThatThrownBy(() -> service.verificar("seu odiento", autenticado(cliente.getId(), TipoUsuario.CLIENTE)))
                .isInstanceOf(ConteudoBloqueadoException.class);

        assertThat(usuarioRepository.findById(cliente.getId()).orElseThrow().getBloqueadoEm()).isNotNull();
    }

    @Test
    void moderadorNuncaEhBloqueado() {
        assertThatThrownBy(() -> service.verificar("seu odiento",
                autenticado(moderacaoRegistry.ids().iterator().next(), TipoUsuario.MODERADOR)))
                .isInstanceOf(ConteudoBloqueadoException.class);

        assertThat(bloqueioRepository.count()).isZero();
        assertThat(notificacaoRepository.findAll().stream()
                .filter(n -> n.getEvento() == EventoNotificacao.CONTA_BLOQUEADA)).isEmpty();
    }

    private UserModel cliente() {
        UserModel u = new UserModel();
        u.setNome("Cliente");
        u.setEmail("cliente-" + UUID.randomUUID() + "@x.com");
        return usuarioRepository.save(u);
    }

    private ChefeModel chefe() {
        ChefeModel c = new ChefeModel();
        c.setNome("Chef");
        c.setEmail("chef-" + UUID.randomUUID() + "@x.com");
        c.setNacionalidade("Brasileira");
        c.setIdiomas("pt");
        c.setValorHora(new BigDecimal("100.00"));
        c.setCidade("São Paulo");
        c.setEstado("SP");
        c.setEspecialidade(Especialidades.BRASILEIRA);
        c.setSenioridade(Senioridade.HEAD_CHEF);
        c.setAnosDeExperiencia(5);
        c.setAtivo(true);
        return chefeRepository.save(c);
    }

    private static UsuarioAutenticado autenticado(UUID id, TipoUsuario tipo) {
        return new UsuarioAutenticado(id, "u@x.com", tipo);
    }
}
