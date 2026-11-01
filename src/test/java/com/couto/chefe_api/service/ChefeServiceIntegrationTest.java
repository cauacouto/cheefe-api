package com.couto.chefe_api.service;

import com.couto.chefe_api.TelefoneDeTeste;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.dto.ChefeResponseDto;
import com.couto.chefe_api.dto.ChefeRequestDto;
import com.couto.chefe_api.exception.ChefeNaoEncontradoException;
import com.couto.chefe_api.exception.ContaInativaException;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.Denuncia;
import com.couto.chefe_api.enums.MotivoDenuncia;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.DenunciaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ChefeServiceIntegrationTest {

    @Autowired
    private ChefeService chefeService;
    @Autowired
    private ChefeRepository chefeRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private DenunciaRepository denunciaRepository;
    @Autowired
    private com.couto.chefe_api.repository.PratosRepository pratosRepository;
    @Autowired
    private com.couto.chefe_api.MenuDeTeste menus;

    private ChefeModel ativo;
    private ChefeModel inativo;

    @BeforeEach
    void criarChefes() {
        ativo = chefeRepository.save(chefe("Ativo", true));
        inativo = chefeRepository.save(chefe("Inativo", false));
        menus.ativo(ativo);
        menus.ativo(inativo);
    }

    @AfterEach
    void limpar() {
        denunciaRepository.deleteAll();
        agendamentoRepository.deleteAll();
        pratosRepository.deleteAll();
        usuarioRepository.deleteAll();
        chefeRepository.deleteAll();
    }

    @Test
    void listagemNaoMostraChefesInativos() {
        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), null, null, null, null, null, null).getContent())
                .extracting(ChefeResponseDto::nome)
                .containsExactly("Ativo");
    }

    @Test
    void listagemComFiltroDeValorTambemEscondeInativos() {
        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), new BigDecimal("1"), new BigDecimal("1000"), null, null, null, null).getContent())
                .extracting(ChefeResponseDto::nome)
                .containsExactly("Ativo");
    }

    @Test
    void destaquesNaoMostramChefesInativos() {
        assertThat(chefeService.chefesMaisAgendados(PageRequest.of(0, 10)).getContent())
                .extracting(ChefeResponseDto::nome)
                .containsExactly("Ativo");
    }

    @Test
    void deletarFazSoftDelete() {
        chefeService.deletar(ativo.getId());

        assertThat(chefeRepository.findById(ativo.getId())).get()
                .extracting(ChefeModel::getAtivo).isEqualTo(false);
    }

    @Test
    void deletarChefeInexistenteDa404() {
        assertThatThrownBy(() -> chefeService.deletar(UUID.randomUUID()))
                .isInstanceOf(ChefeNaoEncontradoException.class);
    }

    @Test
    void pausarEscondeDaBuscaSemBloquearAConta() {
        chefeService.inativar(ativo.getId());

        ChefeModel pausado = chefeRepository.findById(ativo.getId()).orElseThrow();
        assertThat(pausado.getAtivo()).isTrue();
        assertThat(pausado.isDisponivel()).isFalse();
        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), null, null, null, null, null, null).getContent()).isEmpty();
        assertThat(chefeService.chefesMaisAgendados(PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void chefPausadoContinuaEditandoOPerfil() {
        chefeService.inativar(ativo.getId());

        chefeService.atualizarDados(new ChefeRequestDto("Renomeado", null, null, null, null, null, null, null), ativo.getId());

        assertThat(chefeRepository.findById(ativo.getId()).orElseThrow().getNome()).isEqualTo("Renomeado");
    }

    @Test
    void ativarDepoisDaPausaVoltaAAparecer() {
        chefeService.inativar(ativo.getId());

        chefeService.ativar(ativo.getId());

        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), null, null, null, null, null, null).getContent())
                .extracting(ChefeResponseDto::nome)
                .containsExactly("Ativo");
    }

    @Test
    void contaApagadaNaoPodeSerReativada() {
        chefeService.deletar(ativo.getId());

        assertThatThrownBy(() -> chefeService.ativar(ativo.getId()))
                .isInstanceOf(ContaInativaException.class);
    }

    @Test
    void chefIncompletoNaoApareceNaBuscaNemNosDestaques() {
        ChefeModel incompleto = chefe("Incompleto", true);
        incompleto.setSenioridade(null);
        chefeRepository.save(incompleto);

        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), null, null, null, null, null, null).getContent())
                .extracting(ChefeResponseDto::nome).containsExactly("Ativo");
        assertThat(chefeService.chefesMaisAgendados(PageRequest.of(0, 10)).getContent())
                .extracting(ChefeResponseDto::nome).containsExactly("Ativo");
    }

    @Test
    void chefComIdiomasEmBrancoNaoAparece() {
        ChefeModel semIdiomas = chefe("SemIdiomas", true);
        semIdiomas.setIdiomas("   ");
        chefeRepository.save(semIdiomas);

        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), null, null, null, null, null, null).getContent())
                .extracting(ChefeResponseDto::nome).containsExactly("Ativo");
        assertThat(chefeService.chefesMaisAgendados(PageRequest.of(0, 10)).getContent())
                .extracting(ChefeResponseDto::nome).containsExactly("Ativo");
    }

    @Test
    void chefCompletoMasPausadoNaoAparece() {
        chefeService.inativar(ativo.getId());

        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), null, null, null, null, null, null).getContent()).isEmpty();
        assertThat(chefeService.chefesMaisAgendados(PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void chefComEspacosQueOTrimDoBancoNaoRemoveNaoAparece() {
        ChefeModel comTab = chefe("ComTab", true);
        comTab.setNacionalidade("\t");
        chefeRepository.save(comTab);
        ChefeModel comEmSpace = chefe("ComEmSpace", true);
        comEmSpace.setIdiomas("\u2003");
        chefeRepository.save(comEmSpace);

        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), null, null, null, null, null, null).getContent())
                .extracting(ChefeResponseDto::nome).containsExactly("Ativo");
        assertThat(chefeService.chefesMaisAgendados(PageRequest.of(0, 10)).getContent())
                .extracting(ChefeResponseDto::nome).containsExactly("Ativo");
        assertThat(chefeRepository.findById(comTab.getId()).orElseThrow().getNacionalidade()).isNull();
    }

    @Test
    void suspensoNaoApareceNaBuscaNemNosDestaques() {
        ativo.setSuspenso(true);
        chefeRepository.save(ativo);

        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), null, null, null, null, null, null).getContent()).isEmpty();
        assertThat(chefeService.chefesMaisAgendados(PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void comTresDenunciantesAbertosNaoAparece() {
        for (int i = 0; i < 3; i++) {
            denunciar(ativo, StatusDenuncia.ABERTA);
        }

        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), null, null, null, null, null, null).getContent()).isEmpty();
        assertThat(chefeService.chefesMaisAgendados(PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void comDuasDenunciasAbertasOuComArquivadasAparece() {
        denunciar(ativo, StatusDenuncia.ABERTA);
        denunciar(ativo, StatusDenuncia.ABERTA);
        denunciar(ativo, StatusDenuncia.ARQUIVADA);

        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), null, null, null, null, null, null).getContent())
                .extracting(ChefeResponseDto::nome).containsExactly("Ativo");
    }

    private void denunciar(ChefeModel chefe, StatusDenuncia status) {
        UserModel cliente = new UserModel();
        cliente.setNome("Cliente");
        cliente.setEmail("cliente-" + UUID.randomUUID() + "@x.com");
        cliente = usuarioRepository.save(cliente);
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(cliente);
        agendamento.setChefe(chefe);
        agendamento = agendamentoRepository.save(agendamento);
        Denuncia denuncia = new Denuncia();
        denuncia.setChefe(chefe);
        denuncia.setCliente(cliente);
        denuncia.setAgendamento(agendamento);
        denuncia.setMotivo(MotivoDenuncia.OUTRO);
        denuncia.setDescricao("x");
        denuncia.setStatus(status);
        denunciaRepository.save(denuncia);
    }

    private static ChefeModel chefe(String nome, boolean ativo) {
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
        chefe.setAtivo(ativo);
        return TelefoneDeTeste.verificado(chefe);
    }
}
