package com.couto.chefe_api;

import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.domin.Pratos;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.EndercoRepository;
import com.couto.chefe_api.repositorys.PatrosRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova, num Postgres real, que: as migrations batem com as entidades (ddl-auto=validate sobe como
 * chefhome_app); a aplicação só manipula dados; e o setup de usuários é idempotente.
 * Sem Docker o teste é pulado (no CI ele sempre roda).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MigracoesPostgresTest {

    static final String SENHA_MIGRACAO = "migracao-teste";
    static final String SENHA_APP = "app-teste";
    static final String SCRIPT_NO_CONTAINER = "/tmp/01_usuarios.sql";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    ChefeRepository chefeRepository;
    @Autowired
    EndercoRepository enderecoRepository;
    @Autowired
    PatrosRepository pratosRepository;

    @DynamicPropertySource
    static void bancoReal(DynamicPropertyRegistry registry) throws Exception {
        POSTGRES.start();
        rodarSetup();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "chefhome_migracao", SENHA_MIGRACAO)
                .schemas("chefhome")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        registry.add("spring.datasource.url", MigracoesPostgresTest::urlChefhome);
        registry.add("spring.datasource.username", () -> "chefhome_app");
        registry.add("spring.datasource.password", () -> SENHA_APP);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void contextoSobeComValidateComoApp() {
        // subir o contexto com ddl-auto=validate, conectado como chefhome_app, já é a asserção
    }

    @Test
    void appGravaELeEmTabelasComSequence() {
        UserModel usuario = new UserModel();
        usuario.setNome("Ana");
        usuario.setEmail("ana-" + UUID.randomUUID() + "@x.com");
        usuario = usuarioRepository.save(usuario);

        EnderecoModel endereco = new EnderecoModel();
        endereco.setRua("Rua A");
        endereco.setNumero("1");
        endereco.setTipoDeResidencia(TipoDeResidencia.CASA);
        endereco.setUsuario(usuario);
        endereco = enderecoRepository.save(endereco);

        ChefeModel chefe = new ChefeModel();
        chefe.setNome("Bia");
        chefe.setEmail("bia-" + UUID.randomUUID() + "@x.com");
        chefe.setNacionalidade("Brasileira");
        chefe.setIdiomas("pt");
        chefe.setEspecialidade(Especialidades.BRASILEIRA);
        chefe.setSenioridade(Senioridade.HEAD_CHEF);
        chefe.setAnosDeExperiencia(5);
        chefe.setValorHora(new BigDecimal("100"));
        chefe = chefeRepository.save(chefe);

        Pratos prato = new Pratos();
        prato.setNomePrato("Moqueca");
        prato.setChefeModel(chefe);
        prato = pratosRepository.save(prato);

        assertThat(enderecoRepository.findById(endereco.getId())).isPresent();
        assertThat(pratosRepository.findById(prato.getId())).isPresent();
        assertThat(chefeRepository.findById(chefe.getId()).orElseThrow().isSuspenso()).isFalse();
    }

    @Test
    void appNaoCriaNemApagaTabela() throws SQLException {
        try (Connection app = conectar("chefhome_app", SENHA_APP)) {
            assertSemPermissao(app, "CREATE TABLE chefhome.invasao (id int)");
            assertSemPermissao(app, "CREATE TABLE public.invasao (id int)");
            assertSemPermissao(app, "DROP TABLE chefhome.usuarios");
            assertSemPermissao(app, "ALTER TABLE chefhome.usuarios ADD COLUMN x int");
        }
    }

    @Test
    void appNaoAlteraHistoricoDeMigrations() throws SQLException {
        try (Connection app = conectar("chefhome_app", SENHA_APP)) {
            assertSemPermissao(app, "DELETE FROM chefhome.flyway_schema_history");
            assertSemPermissao(app, "UPDATE chefhome.flyway_schema_history SET checksum = 0");
        }
    }

    @Test
    void defaultPrivilegesCobremTabelasFuturas() throws SQLException {
        try (Connection migracao = conectar("chefhome_migracao", SENHA_MIGRACAO);
             Statement st = migracao.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS chefhome.futura (id int)");
        }
        try (Connection app = conectar("chefhome_app", SENHA_APP);
             Statement st = app.createStatement()) {
            st.execute("INSERT INTO chefhome.futura (id) VALUES (1)");
            assertThat(st.executeQuery("SELECT count(*) FROM chefhome.futura").next()).isTrue();
        }
    }

    @Test
    void setupEhIdempotente() throws Exception {
        assertThat(rodarSetup().getExitCode()).isZero();
    }

    static Container.ExecResult rodarSetup() throws Exception {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(Path.of("db/setup/01_usuarios.sql")), SCRIPT_NO_CONTAINER);
        Container.ExecResult resultado = POSTGRES.execInContainer("psql",
                "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName(),
                "-v", "ON_ERROR_STOP=1",
                "-v", "senha_migracao=" + SENHA_MIGRACAO,
                "-v", "senha_app=" + SENHA_APP,
                "-v", "nome_banco=" + POSTGRES.getDatabaseName(),
                "-f", SCRIPT_NO_CONTAINER);
        assertThat(resultado.getExitCode()).as(resultado.getStderr()).isZero();
        return resultado;
    }

    static String urlChefhome() {
        String url = POSTGRES.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=chefhome";
    }

    static Connection conectar(String usuario, String senha) throws SQLException {
        return DriverManager.getConnection(urlChefhome(), usuario, senha);
    }

    static void assertSemPermissao(Connection conexao, String sql) {
        assertThatThrownBy(() -> {
            try (Statement st = conexao.createStatement()) {
                st.execute(sql);
            }
        }).isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
    }
}
