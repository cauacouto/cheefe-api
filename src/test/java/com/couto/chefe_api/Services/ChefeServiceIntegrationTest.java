package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.ChefeResponseDto;
import com.couto.chefe_api.Dtos.ChefeRequestDto;
import com.couto.chefe_api.Excepitons.ChefeNaoEncontradoException;
import com.couto.chefe_api.Excepitons.ContaInativaException;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.repositorys.ChefeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
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

    private ChefeModel ativo;
    private ChefeModel inativo;

    @BeforeEach
    void criarChefes() {
        ativo = chefeRepository.save(chefe("Ativo", true));
        inativo = chefeRepository.save(chefe("Inativo", false));
    }

    @AfterEach
    void limpar() {
        chefeRepository.deleteAll();
    }

    @Test
    void listagemNaoMostraChefesInativos() {
        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), null, null).getContent())
                .extracting(ChefeResponseDto::nome)
                .containsExactly("Ativo");
    }

    @Test
    void listagemComFiltroDeValorTambemEscondeInativos() {
        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), new BigDecimal("1"), new BigDecimal("1000")).getContent())
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
        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), null, null).getContent()).isEmpty();
        assertThat(chefeService.chefesMaisAgendados(PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void chefPausadoContinuaEditandoOPerfil() {
        chefeService.inativar(ativo.getId());

        chefeService.atualizarDados(new ChefeRequestDto("Renomeado", null, null, null, null, null, null), ativo.getId());

        assertThat(chefeRepository.findById(ativo.getId()).orElseThrow().getNome()).isEqualTo("Renomeado");
    }

    @Test
    void ativarDepoisDaPausaVoltaAAparecer() {
        chefeService.inativar(ativo.getId());

        chefeService.ativar(ativo.getId());

        assertThat(chefeService.mostrarChefes(PageRequest.of(0, 10), null, null).getContent())
                .extracting(ChefeResponseDto::nome)
                .containsExactly("Ativo");
    }

    @Test
    void contaApagadaNaoPodeSerReativada() {
        chefeService.deletar(ativo.getId());

        assertThatThrownBy(() -> chefeService.ativar(ativo.getId()))
                .isInstanceOf(ContaInativaException.class);
    }

    private static ChefeModel chefe(String nome, boolean ativo) {
        ChefeModel chefe = new ChefeModel();
        chefe.setNome(nome);
        chefe.setEmail(nome.toLowerCase() + "-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setValorHora(new BigDecimal("100.00"));
        chefe.setAtivo(ativo);
        return chefe;
    }
}
