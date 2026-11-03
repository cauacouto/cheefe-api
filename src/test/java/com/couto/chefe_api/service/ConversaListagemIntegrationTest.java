package com.couto.chefe_api.service;

import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.dto.ConversaDto;
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
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Set;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** GET /conversas: número de queries constante (sem N+1), qualquer que seja o número de conversas na página. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class ConversaListagemIntegrationTest {

    @Autowired ChatService chatService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired ConversaRepository conversaRepository;
    @Autowired MensagemRepository mensagemRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired EntityManagerFactory entityManagerFactory;

    UserModel ana;
    UsuarioAutenticado anaLogada;

    @BeforeEach
    void setUp() {
        ana = new UserModel();
        ana.setNome("Ana");
        ana.setEmail("ana-" + UUID.randomUUID() + "@x.com");
        ana.setEndereco(new ArrayList<>());
        ana = usuarioRepository.save(TelefoneDeTeste.verificado(ana));
        anaLogada = new UsuarioAutenticado(ana.getId(), ana.getEmail(), TipoUsuario.CLIENTE);
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
    void quantidadeDeQueriesNaoCresceComAsConversas() {
        conversasComMensagens(2);
        long comDuas = queriesAoListar();
        conversasComMensagens(6);
        long comOito = queriesAoListar();

        assertThat(comOito).isEqualTo(comDuas);
        assertThat(comOito).isLessThanOrEqualTo(4);
    }

    @Test
    void cadaConversaRecebeAPropriaUltimaMensagemEContagem() {
        ChefeModel bia = chefe("Bia");
        ChefeModel caio = chefe("Caio");
        long comBia = chatService.abrirConversa(ana.getId(), bia.getId()).conversa().id();
        long comCaio = chatService.abrirConversa(ana.getId(), caio.getId()).conversa().id();
        UsuarioAutenticado biaLogada = new UsuarioAutenticado(bia.getId(), bia.getEmail(), TipoUsuario.CHEFE);
        UsuarioAutenticado caioLogado = new UsuarioAutenticado(caio.getId(), caio.getEmail(), TipoUsuario.CHEFE);
        chatService.enviar(comBia, biaLogada, "b1");
        chatService.enviar(comBia, biaLogada, "b2");
        chatService.enviar(comCaio, caioLogado, "c1");
        chatService.enviar(comBia, anaLogada, "minha");

        Page<ConversaDto> pagina = chatService.listarConversas(anaLogada, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
        ConversaDto primeira = pagina.getContent().get(0);
        ConversaDto segunda = pagina.getContent().get(1);
        assertThat(primeira.id()).isEqualTo(comBia);
        assertThat(primeira.outroParticipante().nome()).isEqualTo("Bia");
        assertThat(primeira.ultimaMensagem().texto()).isEqualTo("minha");
        assertThat(primeira.ultimaMensagem().minha()).isTrue();
        assertThat(primeira.naoLidas()).isEqualTo(2);
        assertThat(segunda.id()).isEqualTo(comCaio);
        assertThat(segunda.ultimaMensagem().texto()).isEqualTo("c1");
        assertThat(segunda.naoLidas()).isEqualTo(1);
    }

    @Test
    void paginaComTamanhoMaximo() {
        conversasComMensagens(3);

        Page<ConversaDto> pagina = chatService.listarConversas(anaLogada, PageRequest.of(0, 2));
        Page<ConversaDto> grande = chatService.listarConversas(anaLogada, PageRequest.of(0, 1000));

        assertThat(pagina.getContent()).hasSize(2);
        assertThat(pagina.getTotalElements()).isEqualTo(3);
        assertThat(grande.getSize()).isEqualTo(ChatService.TAMANHO_MAXIMO_PAGINA_CONVERSAS);
    }

    private long queriesAoListar() {
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
        Page<ConversaDto> pagina = chatService.listarConversas(anaLogada, PageRequest.of(0, 20));
        assertThat(pagina.getContent()).allSatisfy(c -> {
            assertThat(c.outroParticipante().nome()).isNotBlank();
            assertThat(c.ultimaMensagem()).isNotNull();
        });
        return estatisticas.getPrepareStatementCount();
    }

    private void conversasComMensagens(int quantidade) {
        for (int i = 0; i < quantidade; i++) {
            ChefeModel chefe = chefe("Chef " + i);
            long conversa = chatService.abrirConversa(ana.getId(), chefe.getId()).conversa().id();
            chatService.enviar(conversa, new UsuarioAutenticado(chefe.getId(), chefe.getEmail(), TipoUsuario.CHEFE), "oi " + i);
        }
    }

    private ChefeModel chefe(String nome) {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome(nome);
        chefe.setEmail("chef-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
        chefe.setEspecialidades(Set.of(Especialidades.BRASILEIRA));
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        return chefeRepository.save(TelefoneDeTeste.verificado(chefe));
    }
}
