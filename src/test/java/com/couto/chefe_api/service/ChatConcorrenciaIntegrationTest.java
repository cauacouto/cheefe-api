package com.couto.chefe_api.service;

import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.ConversaRepository;
import com.couto.chefe_api.repository.MensagemRepository;
import com.couto.chefe_api.repository.NotificacaoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Pedidos simultâneos (duplo clique, duas abas): o resultado tem de ser o mesmo que em sequência. */
@SpringBootTest
@ActiveProfiles("test")
class ChatConcorrenciaIntegrationTest {

    static final int PEDIDOS = 8;

    @Autowired ChatService chatService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired ConversaRepository conversaRepository;
    @Autowired MensagemRepository mensagemRepository;
    @Autowired NotificacaoRepository notificacaoRepository;

    UserModel ana;
    ChefeModel bia;

    @BeforeEach
    void setUp() {
        ana = new UserModel();
        ana.setNome("Ana");
        ana.setEmail("ana-" + UUID.randomUUID() + "@x.com");
        ana.setEndereco(new ArrayList<>());
        ana = usuarioRepository.save(TelefoneDeTeste.verificado(ana));
        bia = new ChefeModel();
        bia.setNome("Bia");
        bia.setEmail("bia-" + UUID.randomUUID() + "@x.com");
        bia.setNacionalidade("Brasileira");
        bia.setIdiomas("pt");
        bia.setCidade("São Paulo");
        bia.setEstado("SP");
        bia.setEspecialidades(Set.of(Especialidades.BRASILEIRA));
        bia.setSenioridade(Senioridade.HEAD_CHEF);
        bia.setAnosDeExperiencia(5);
        bia = chefeRepository.save(TelefoneDeTeste.verificado(bia));
    }

    @AfterEach
    void limparBanco() {
        mensagemRepository.deleteAll();
        conversaRepository.deleteAll();
        notificacaoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void abrirAMesmaConversaAoMesmoTempoDevolveAMesmaSemErro() throws Exception {
        List<ChatService.ConversaAberta> abertas = emParalelo(() -> chatService.abrirConversa(ana.getId(), bia.getId()));

        assertThat(abertas).extracting(a -> a.conversa().id()).containsOnly(abertas.get(0).conversa().id());
        assertThat(abertas).filteredOn(ChatService.ConversaAberta::criada).hasSize(1);
        assertThat(conversaRepository.count()).isEqualTo(1);
    }

    @Test
    void mensagensSimultaneasGeramUmaUnicaNotificacao() throws Exception {
        long conversa = chatService.abrirConversa(ana.getId(), bia.getId()).conversa().id();
        UsuarioAutenticado remetente = new UsuarioAutenticado(ana.getId(), ana.getEmail(), TipoUsuario.CLIENTE);

        emParalelo(() -> chatService.enviar(conversa, remetente, "oi"));

        assertThat(mensagemRepository.count()).isEqualTo(PEDIDOS);
        assertThat(notificacaoRepository.countByDestinatarioIdAndLidaFalse(bia.getId())).isEqualTo(1);
    }

    private <T> List<T> emParalelo(Callable<T> pedido) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(PEDIDOS);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (int i = 0; i < PEDIDOS; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return pedido.call();
                }));
            }
            largada.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }
}
