package com.couto.chefe_api.User;

import com.couto.chefe_api.Dtos.EnderecoRequestDto;
import com.couto.chefe_api.Dtos.UserRequestDto;
import com.couto.chefe_api.Dtos.UserResponseDto;
import com.couto.chefe_api.Excepitons.EnderecoInvalidoException;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.repositorys.EndercoRepository;
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
    private EndercoRepository enderecoRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

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
        enderecoRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void substituiOsEnderecosSemAcumular() {
        UserRequestDto dto = new UserRequestDto("Ana Maria", "ana-nova-" + UUID.randomUUID() + "@x.com", "11888888888",
                List.of(endereco("Rua Nova", TipoDeResidencia.CASA, null),
                        endereco("Rua do Prédio", TipoDeResidencia.APARTAMENTO, "Bloco B ap 12")));

        UserResponseDto resposta = usuarioService.atualizarUsuario(dto, usuarioId);

        assertThat(resposta.getNome()).isEqualTo("Ana Maria");
        assertThat(resposta.getEndereco()).extracting("rua").containsExactlyInAnyOrder("Rua Nova", "Rua do Prédio");
        assertThat(enderecosDoUsuario()).extracting(EnderecoModel::getRua)
                .containsExactlyInAnyOrder("Rua Nova", "Rua do Prédio");
    }

    @Test
    void funcionaComEntidadeGerenciadaComoNoOpenSessionInView() {
        UserRequestDto dto = new UserRequestDto("Ana Maria", "ana-osiv-" + UUID.randomUUID() + "@x.com", "11888888888",
                List.of(endereco("Rua Nova", TipoDeResidencia.CASA, null)));

        transactionTemplate.executeWithoutResult(status -> {
            usuarioRepository.findById(usuarioId).orElseThrow();
            usuarioService.atualizarUsuario(dto, usuarioId);
        });

        assertThat(enderecosDoUsuario()).extracting(EnderecoModel::getRua).containsExactly("Rua Nova");
    }

    @Test
    void enderecoInvalidoNaoAlteraNada() {
        UserRequestDto dto = new UserRequestDto("Outro nome", "outro-" + UUID.randomUUID() + "@x.com", "11",
                List.of(endereco("Rua do Prédio", TipoDeResidencia.APARTAMENTO, " ")));

        assertThatThrownBy(() -> usuarioService.atualizarUsuario(dto, usuarioId))
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
}
