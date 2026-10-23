package com.couto.chefe_api.service;

import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.Avaliacao;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.AvaliacaoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Comentário com nome sai da espera 7 dias depois da conclusão, mesmo sem o chef avaliar o cliente. */
@SpringBootTest
@ActiveProfiles("test")
class AvaliacaoRotinaIntegrationTest {

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ChefeRepository chefeRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired AgendamentoRepository agendamentoRepository;
    @Autowired AvaliacaoRepository avaliacaoRepository;
    @Autowired AvaliacaoRotina rotina;
    @Autowired List<org.springframework.scheduling.config.ScheduledTaskHolder> agendadores;

    @AfterEach
    void limparBanco() {
        agendamentoRepository.deleteAll();
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void nosTestesARotinaNaoRodaSozinha() {
        assertThat(agendadores.stream().flatMap(h -> h.getScheduledTasks().stream()).map(Object::toString))
                .doesNotContain(AvaliacaoRotina.class.getName() + ".publicar");
    }

    @Test
    void publicaOComentarioComNomeDepoisDos7DiasEUmaVezSo() {
        ChefeModel bia = novoChefe();
        LocalDateTime agora = LocalDateTime.now();
        Avaliacao venceu = comNome(bia, agora.minusDays(7).minusMinutes(1));
        Avaliacao noPrazo = comNome(bia, agora.minusDays(6));

        rotina.publicar();

        LocalDateTime publicadaEm = avaliacaoRepository.findById(venceu.getId()).orElseThrow().getPublicadoEm();
        assertThat(publicadaEm).isNotNull();
        assertThat(avaliacaoRepository.findById(noPrazo.getId()).orElseThrow().getPublicadoEm()).isNull();

        rotina.publicar();

        assertThat(avaliacaoRepository.findById(venceu.getId()).orElseThrow().getPublicadoEm()).isEqualTo(publicadaEm);
    }

    private Avaliacao comNome(ChefeModel chef, LocalDateTime concluidoEm) {
        UserModel cliente = new UserModel();
        cliente.setNome("Ana");
        cliente.setEmail("ana-" + UUID.randomUUID() + "@x.com");
        cliente.setEndereco(new ArrayList<>());
        cliente = usuarioRepository.save(cliente);
        EnderecoModel endereco = new EnderecoModel();
        endereco.setRua("Rua A");
        endereco.setNumero("1");
        endereco.setBairro("Centro");
        endereco.setCidade("São Paulo");
        endereco.setEstado("SP");
        endereco.setCep("01001-000");
        endereco.setTipoDeResidencia(TipoDeResidencia.CASA);
        endereco.setUsuario(cliente);
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setEndereco(enderecoRepository.save(endereco));
        agendamento.setChefes(new ArrayList<>(List.of(chef)));
        agendamento.setDataHoraInicial(concluidoEm.minusHours(2));
        agendamento.setDataHoraFinal(concluidoEm);
        agendamento.setQuantidadePessoas(2);
        agendamento.setValorTotal(new BigDecimal("200.00"));
        agendamento.setStatus(StatusAgendamento.CONCLUIDO);
        agendamento.setConcluidoEm(concluidoEm);
        agendamento = agendamentoRepository.save(agendamento);

        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setAgendamento(agendamento);
        avaliacao.setAutorId(cliente.getId());
        avaliacao.setAutorTipo(TipoUsuario.CLIENTE);
        avaliacao.setAvaliadoId(chef.getId());
        avaliacao.setAvaliadoTipo(TipoUsuario.CHEFE);
        avaliacao.setNota(5);
        avaliacao.setComentario("Ótima");
        avaliacao.setAnonimo(false);
        return avaliacaoRepository.save(avaliacao);
    }

    private ChefeModel novoChefe() {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome("Bia");
        chefe.setEmail("bia-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("100"));
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setDisponivel(true);
        chefe.setAtivo(true);
        return chefeRepository.save(chefe);
    }
}
