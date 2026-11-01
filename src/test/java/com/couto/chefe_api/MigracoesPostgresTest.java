package com.couto.chefe_api;

import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.Pratos;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;
import com.couto.chefe_api.enums.TipoDeResidencia;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.repository.PratosRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
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
import java.sql.ResultSet;
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

    // log_statement=all simula um servidor de produção com auditoria de comandos ligada
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withCommand("postgres", "-c", "fsync=off", "-c", "log_statement=all");

    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    ChefeRepository chefeRepository;
    @Autowired
    EnderecoRepository enderecoRepository;
    @Autowired
    PratosRepository pratosRepository;
    @Autowired
    com.couto.chefe_api.repository.TelefoneBanidoRepository telefoneBanidoRepository;
    @Autowired
    org.springframework.transaction.support.TransactionTemplate transacao;

    @DynamicPropertySource
    static void bancoReal(DynamicPropertyRegistry registry) throws Exception {
        configurarBancoReal(registry);
    }

    /** Container, setup das roles e migrations; a API conecta como chefhome_app com validate. Reusado por outros testes. */
    static void configurarBancoReal(DynamicPropertyRegistry registry) throws Exception {
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
        chefe.setCidade("São Paulo");
        chefe.setEstado("SP");
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

    /**
     * O validate do Hibernate não confere CHECKs: um valor novo num enum passaria nos testes e só
     * falharia em produção no primeiro INSERT. Aqui cada CHECK da migration é comparado com o enum Java.
     */
    @Test
    void auditoriaDaModeracaoEhSoDeInsercaoParaAApi() throws Exception {
        rodarSetup(); // re-rodar o setup (ex.: trocar senha) não pode devolver UPDATE/DELETE à API
        try (Connection app = conectar("chefhome_app", SENHA_APP);
             Statement st = app.createStatement()) {
            st.execute("INSERT INTO chefhome.acao_moderacao (moderador_email, acao, realizada_em) "
                    + "VALUES ('m@x.com', 'BANIR_CHEF', now())");
            assertThat(st.executeQuery("SELECT count(*) FROM chefhome.acao_moderacao").next()).isTrue();
            assertSemPermissao(app, "UPDATE chefhome.acao_moderacao SET moderador_email = 'outro@x.com'");
            assertSemPermissao(app, "DELETE FROM chefhome.acao_moderacao");
            assertSemPermissao(app, "TRUNCATE chefhome.acao_moderacao");
        }
    }

    static final String CHEFE_DE_TESTE = "00000000-0000-0000-0000-0000000009ff";

    /** Todo agendamento tem chef (V15): um chef só para estes testes de tabela, apagado ao fim de cada um. */
    static void agendamentoDeTeste(Statement st, long id, String status) throws SQLException {
        st.execute("INSERT INTO chefhome.chefe (id, email, nome) VALUES ('" + CHEFE_DE_TESTE + "', 'teste-v15@x.com', 'Teste') "
                + "ON CONFLICT DO NOTHING");
        st.execute("INSERT INTO chefhome.agendamentos (id, status, chefe_id) VALUES (" + id + ", '" + status + "', '"
                + CHEFE_DE_TESTE + "')");
    }

    @AfterEach
    void apagarChefeDeTeste() throws SQLException {
        try (Connection app = conectar("chefhome_app", SENHA_APP);
             Statement st = app.createStatement()) {
            st.execute("DELETE FROM chefhome.chefe WHERE id = '" + CHEFE_DE_TESTE + "'");
        }
    }

    @Test
    void historicoDeAgendamentoEhSoDeInsercaoParaAApi() throws Exception {
        rodarSetup(); // re-rodar o setup não pode devolver UPDATE/DELETE à API
        try (Connection app = conectar("chefhome_app", SENHA_APP);
             Statement st = app.createStatement()) {
            agendamentoDeTeste(st, 900001, "PENDENTE");
            st.execute("INSERT INTO chefhome.agendamento_historico (agendamento_id, acao, ocorrido_em) "
                    + "VALUES (900001, 'CRIADO', now())");
            assertSemPermissao(app, "UPDATE chefhome.agendamento_historico SET motivo = 'OUTRO'");
            assertSemPermissao(app, "DELETE FROM chefhome.agendamento_historico");
            assertSemPermissao(app, "TRUNCATE chefhome.agendamento_historico");
            st.execute("DELETE FROM chefhome.agendamentos WHERE id = 900001");
        }
    }

    @Test
    void apagarAgendamentoLevaOHistorico() throws Exception {
        try (Connection app = conectar("chefhome_app", SENHA_APP);
             Statement st = app.createStatement()) {
            agendamentoDeTeste(st, 900002, "CONFIRMADO");
            st.execute("INSERT INTO chefhome.agendamento_historico (agendamento_id, acao, ocorrido_em) "
                    + "VALUES (900002, 'CRIADO', now()), (900002, 'CONFIRMADO', now())");

            // a API não apaga histórico, mas apagar o agendamento (exclusão de conta) leva as linhas pela cascata
            st.execute("DELETE FROM chefhome.agendamentos WHERE id = 900002");

            assertThat(coluna(st, "SELECT count(*)::text FROM chefhome.agendamento_historico WHERE agendamento_id = 900002"))
                    .containsExactly("0");
        }
    }

    @Test
    void apagarAgendamentoLevaAvaliacoesTagsEDenuncias() throws Exception {
        try (Connection app = conectar("chefhome_app", SENHA_APP);
             Statement st = app.createStatement()) {
            agendamentoDeTeste(st, 900003, "CONCLUIDO");
            try {
                st.execute("INSERT INTO chefhome.avaliacao (id, agendamento_id, autor_id, autor_tipo, avaliado_id, avaliado_tipo, "
                        + "nota, comentario, anonimo, publicado_em, comentario_removido, criada_em) VALUES "
                        + "(900003, 900003, gen_random_uuid(), 'CLIENTE', gen_random_uuid(), 'CHEFE', 5, 'ótimo', true, now(), false, now())");
                st.execute("INSERT INTO chefhome.avaliacao_tag (avaliacao_id, tag) VALUES (900003, 'PONTUAL'), (900003, 'SIMPATICO')");
                st.execute("INSERT INTO chefhome.denuncia_avaliacao (avaliacao_id, denunciante_id, denunciante_tipo, autor_id, autor_tipo, "
                        + "texto_denunciado, motivo, status, criada_em) VALUES "
                        + "(900003, gen_random_uuid(), 'CHEFE', gen_random_uuid(), 'CLIENTE', 'ótimo', 'SPAM', 'ABERTA', now())");

                st.execute("DELETE FROM chefhome.agendamentos WHERE id = 900003");

                assertThat(coluna(st, "SELECT count(*)::text FROM chefhome.avaliacao WHERE id = 900003")).containsExactly("0");
                assertThat(coluna(st, "SELECT count(*)::text FROM chefhome.avaliacao_tag WHERE avaliacao_id = 900003")).containsExactly("0");
                assertThat(coluna(st, "SELECT count(*)::text FROM chefhome.denuncia_avaliacao WHERE avaliacao_id = 900003")).containsExactly("0");
            } finally {
                st.execute("DELETE FROM chefhome.agendamentos WHERE id = 900003");
            }
        }
    }

    @Test
    void notaDaAvaliacaoVaiDeUmACinco() throws Exception {
        try (Connection app = conectar("chefhome_app", SENHA_APP);
             Statement st = app.createStatement()) {
            agendamentoDeTeste(st, 900004, "CONCLUIDO");
            try {
                for (int nota : new int[]{0, 6}) {
                    assertThatThrownBy(() -> st.execute("INSERT INTO chefhome.avaliacao (agendamento_id, autor_id, autor_tipo, "
                            + "avaliado_id, avaliado_tipo, nota, criada_em) VALUES (900004, gen_random_uuid(), 'CLIENTE', "
                            + "gen_random_uuid(), 'CHEFE', " + nota + ", now())"))
                            .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
                }
            } finally {
                st.execute("DELETE FROM chefhome.agendamentos WHERE id = 900004");
            }
        }
    }

    /** V12: um telefone verificado por conta em cada tabela (vários nulos podem); hash de banido só entra e sai. */
    @Test
    void telefoneVerificadoUnicoPorTabelaEBanidoSoDeInsercaoEExclusao() throws Exception {
        try (Connection app = conectar("chefhome_app", SENHA_APP);
             Statement st = app.createStatement()) {
            for (String[] indice : new String[][]{{"usuarios", "uk_usuarios_telefone_verificado"},
                    {"chefe", "uk_chefe_telefone_verificado"}}) {
                assertThat(coluna(st, "SELECT indexdef FROM pg_indexes WHERE schemaname = 'chefhome' AND indexname = '"
                        + indice[1] + "'")).singleElement().asString()
                        .contains("UNIQUE").contains("(telefone_verificado)").contains("WHERE (telefone_verificado IS NOT NULL)");
            }
            String a = UUID.randomUUID().toString();
            String b = UUID.randomUUID().toString();
            try {
                st.execute("INSERT INTO chefhome.usuarios (id, email, nome) VALUES ('" + a + "', '" + a + "@x.com', 'A'), "
                        + "('" + b + "', '" + b + "@x.com', 'B')");
                st.execute("UPDATE chefhome.usuarios SET telefone_verificado = '+5531999990001' WHERE id = '" + a + "'");
                assertThatThrownBy(() -> st.execute("UPDATE chefhome.usuarios SET telefone_verificado = '+5531999990001' "
                        + "WHERE id = '" + b + "'"))
                        .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23505"));
            } finally {
                st.execute("DELETE FROM chefhome.usuarios WHERE id IN ('" + a + "', '" + b + "')");
            }

            // ON CONFLICT DO NOTHING do Hibernate no Postgres: o mesmo hash duas vezes não é erro
            String hash = "a".repeat(64);
            transacao.executeWithoutResult(t -> telefoneBanidoRepository.banirSeAusente(hash, java.time.LocalDate.now()));
            transacao.executeWithoutResult(t -> telefoneBanidoRepository.banirSeAusente(hash, java.time.LocalDate.now()));
            assertThat(coluna(st, "SELECT count(*)::text FROM chefhome.telefone_banido WHERE hash = '" + hash + "'"))
                    .containsExactly("1");
            assertSemPermissao(app, "UPDATE chefhome.telefone_banido SET banido_em = now()");
            assertSemPermissao(app, "TRUNCATE chefhome.telefone_banido");
            st.execute("DELETE FROM chefhome.telefone_banido WHERE hash = '" + hash + "'");
        }
    }

    @Test
    void notificacaoTemIndicePorReferencia() throws SQLException {
        try (Connection app = conectar("chefhome_app", SENHA_APP);
             Statement st = app.createStatement();
             ResultSet rs = st.executeQuery("SELECT indexdef FROM pg_indexes WHERE schemaname = 'chefhome' "
                     + "AND indexname = 'idx_notificacao_referencia'")) {
            assertThat(rs.next()).as("índice idx_notificacao_referencia").isTrue();
            assertThat(rs.getString(1)).contains("(referencia_id, evento)");
        }
    }

    @Test
    void avaliacaoTagTemChavePrimariaComposta() throws SQLException {
        try (Connection app = conectar("chefhome_app", SENHA_APP);
             Statement st = app.createStatement()) {
            assertThat(coluna(st, "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c "
                    + "JOIN pg_class t ON t.oid = c.conrelid JOIN pg_namespace n ON n.oid = t.relnamespace "
                    + "WHERE n.nspname = 'chefhome' AND t.relname = 'avaliacao_tag' AND c.contype = 'p'"))
                    .containsExactly("PRIMARY KEY (avaliacao_id, tag)");
        }
    }

    @Test
    void avaliacaoTemIndicePorAutorEIndiceParcialDaRotina() throws SQLException {
        try (Connection app = conectar("chefhome_app", SENHA_APP);
             Statement st = app.createStatement()) {
            assertThat(coluna(st, "SELECT indexdef FROM pg_indexes WHERE schemaname = 'chefhome' "
                    + "AND indexname = 'idx_avaliacao_autor'")).singleElement().asString().contains("(autor_id)");
            assertThat(coluna(st, "SELECT indexdef FROM pg_indexes WHERE schemaname = 'chefhome' "
                    + "AND indexname = 'idx_avaliacao_a_publicar'")).singleElement().asString()
                    .contains("(agendamento_id)").contains("publicado_em IS NULL").contains("anonimo = false");
        }
    }

    @Test
    void checksDeEnumBatemComOsEnumsJava() throws SQLException {
        assertCheckDeTexto("chefe", "especialidade", com.couto.chefe_api.enums.Especialidades.values());
        assertCheckDeTexto("chefe", "senioridade", Senioridade.values());
        assertCheckDeTexto("notificacao", "destinatario_tipo", com.couto.chefe_api.enums.TipoUsuario.values());
        assertCheckDeTexto("notificacao", "evento", com.couto.chefe_api.enums.EventoNotificacao.values());
        assertCheckDeTexto("denuncia", "motivo", com.couto.chefe_api.enums.MotivoDenuncia.values());
        assertCheckDeTexto("denuncia", "status", com.couto.chefe_api.enums.StatusDenuncia.values());
        assertCheckDeTexto("acao_moderacao", "acao", com.couto.chefe_api.enums.TipoAcaoModeracao.values());
        assertCheckDeTexto("mensagem", "remetente_tipo", com.couto.chefe_api.enums.TipoUsuario.values());
        assertCheckDeTexto("denuncia_mensagem", "motivo", com.couto.chefe_api.enums.MotivoDenunciaMensagem.values());
        assertCheckDeTexto("denuncia_mensagem", "status", com.couto.chefe_api.enums.StatusDenuncia.values());
        assertCheckDeTexto("denuncia_mensagem", "denunciante_tipo", com.couto.chefe_api.enums.TipoUsuario.values());
        assertCheckDeTexto("denuncia_mensagem", "autor_tipo", com.couto.chefe_api.enums.TipoUsuario.values());
        assertCheckDeTexto("agendamentos", "status", com.couto.chefe_api.enums.StatusAgendamento.values());
        assertCheckDeTexto("endereco", "tipo_de_residencia", TipoDeResidencia.values());
        assertCheckDeTexto("bloqueio_conta", "categoria", java.util.List.of(
                com.couto.chefe_api.enums.CategoriaConteudo.ODIO.name(), com.couto.chefe_api.enums.CategoriaConteudo.AMEACA.name()));
        assertCheckDeTexto("bloqueio_conta", "status", com.couto.chefe_api.enums.StatusBloqueio.values());
        assertCheckDeTexto("bloqueio_conta", "conta_tipo",
                java.util.List.of(com.couto.chefe_api.enums.TipoUsuario.CLIENTE.name(), com.couto.chefe_api.enums.TipoUsuario.CHEFE.name()));
        java.util.List<String> tpUsuario = java.util.List.of(
                com.couto.chefe_api.enums.TipoUsuario.CLIENTE.name(), com.couto.chefe_api.enums.TipoUsuario.CHEFE.name());
        assertCheckDeTexto("avaliacao", "autor_tipo", tpUsuario);
        assertCheckDeTexto("avaliacao", "avaliado_tipo", tpUsuario);
        assertCheckDeTexto("avaliacao_tag", "tag", java.util.stream.Stream.concat(
                        java.util.Arrays.stream(com.couto.chefe_api.enums.TagAvaliacaoChef.values()),
                        java.util.Arrays.stream(com.couto.chefe_api.enums.TagAvaliacaoCliente.values()))
                .map(Enum::name).distinct().toList());
        assertCheckDeTexto("denuncia_avaliacao", "motivo", com.couto.chefe_api.enums.MotivoDenunciaAvaliacao.values());
        assertCheckDeTexto("denuncia_avaliacao", "status", com.couto.chefe_api.enums.StatusDenuncia.values());
        assertCheckDeTexto("denuncia_avaliacao", "denunciante_tipo", com.couto.chefe_api.enums.TipoUsuario.values());
        assertCheckDeTexto("denuncia_avaliacao", "autor_tipo", com.couto.chefe_api.enums.TipoUsuario.values());
        assertCheckDeTexto("agendamento_historico", "acao", com.couto.chefe_api.enums.AcaoAgendamento.values());
        assertCheckDeTexto("agendamento_historico", "autor_tipo",
                java.util.List.of(com.couto.chefe_api.enums.TipoUsuario.CLIENTE.name(), com.couto.chefe_api.enums.TipoUsuario.CHEFE.name()));
        assertCheckDeTexto("agendamento_historico", "motivo", java.util.stream.Stream.concat(
                        java.util.Arrays.stream(com.couto.chefe_api.enums.MotivoCancelamento.values()),
                        java.util.Arrays.stream(com.couto.chefe_api.enums.MotivoRecusa.values()))
                .map(Enum::name).distinct().toList());
    }

    static String definicaoDoCheck(String tabela, String coluna) throws SQLException {
        String sql = "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c "
                + "JOIN pg_class t ON t.oid = c.conrelid JOIN pg_namespace n ON n.oid = t.relnamespace "
                + "WHERE n.nspname = 'chefhome' AND t.relname = ? AND c.contype = 'c' "
                + "AND pg_get_constraintdef(c.oid) LIKE ?";
        try (Connection app = conectar("chefhome_app", SENHA_APP);
             var st = app.prepareStatement(sql)) {
            st.setString(1, tabela);
            st.setString(2, "%" + coluna + "%");
            var rs = st.executeQuery();
            assertThat(rs.next()).as("CHECK de %s.%s", tabela, coluna).isTrue();
            return rs.getString(1);
        }
    }

    static void assertCheckDeTexto(String tabela, String coluna, Enum<?>[] valores) throws SQLException {
        assertCheckDeTexto(tabela, coluna, java.util.Arrays.stream(valores).map(Enum::name).toList());
    }

    static void assertCheckDeTexto(String tabela, String coluna, java.util.List<String> esperados) throws SQLException {
        String definicao = definicaoDoCheck(tabela, coluna);
        java.util.List<String> noBanco = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("'([A-Z_]+)'").matcher(definicao);
        while (m.find()) {
            noBanco.add(m.group(1));
        }
        assertThat(noBanco).as("CHECK de %s.%s: %s", tabela, coluna, definicao)
                .containsExactlyInAnyOrderElementsOf(esperados);
    }

    /**
     * V5 sobre dados que já existem: enums gravados pela posição viram o nome, a tabela de junção
     * troca de nome sem perder linhas e a coluna morta chefe.password some. Banco próprio no mesmo
     * container, migrado só até a V4 antes de inserir os dados no formato antigo.
     */
    @Test
    void v5ConverteOsDadosExistentes() throws Exception {
        executarComoSuperusuario(POSTGRES, "CREATE DATABASE conversao_v5");
        Container.ExecResult setup = POSTGRES.execInContainer("psql",
                "-U", POSTGRES.getUsername(), "-d", "conversao_v5", "-v", "ON_ERROR_STOP=1",
                "-v", "senha_migracao=" + SENHA_MIGRACAO, "-v", "senha_app=" + SENHA_APP,
                "-v", "nome_banco=conversao_v5", "-f", SCRIPT_NO_CONTAINER);
        assertThat(setup.getExitCode()).as(setup.getStderr()).isZero();
        String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/conversao_v5")
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=chefhome";
        Flyway.configure().dataSource(url, "chefhome_migracao", SENHA_MIGRACAO).schemas("chefhome")
                .locations("classpath:db/migration").target("4").load().migrate();

        try (Connection app = DriverManager.getConnection(url, "chefhome_app", SENHA_APP);
             Statement st = app.createStatement()) {
            st.execute("INSERT INTO usuarios (id, email, nome) VALUES ('00000000-0000-0000-0000-000000000001', 'a@x.com', 'Ana')");
            st.execute("INSERT INTO chefe (id, email, nome, password) VALUES "
                    + "('00000000-0000-0000-0000-000000000002', 'b@x.com', 'Bia', 'resto-do-otp')");
            st.execute("INSERT INTO endereco (id, user_id, rua, tipo_de_residencia) VALUES "
                    + "(1, '00000000-0000-0000-0000-000000000001', 'Rua A', 0), "
                    + "(2, '00000000-0000-0000-0000-000000000001', 'Rua B', 1)");
            st.execute("INSERT INTO agendamentos (id, user_id, endereco_id, status) VALUES "
                    + "(1, '00000000-0000-0000-0000-000000000001', 1, 0), (2, '00000000-0000-0000-0000-000000000001', 1, 1), "
                    + "(3, '00000000-0000-0000-0000-000000000001', 2, 2), (4, '00000000-0000-0000-0000-000000000001', 2, 3)");
            st.execute("INSERT INTO agedamento_chefe (agendamento_id, chefe_id) VALUES "
                    + "(1, '00000000-0000-0000-0000-000000000002'), (2, '00000000-0000-0000-0000-000000000002'), "
                    + "(3, '00000000-0000-0000-0000-000000000002'), (4, '00000000-0000-0000-0000-000000000002')");
        }

        Flyway.configure().dataSource(url, "chefhome_migracao", SENHA_MIGRACAO).schemas("chefhome")
                .locations("classpath:db/migration").load().migrate();

        try (Connection app = DriverManager.getConnection(url, "chefhome_app", SENHA_APP);
             Statement st = app.createStatement()) {
            assertThat(coluna(st, "SELECT status FROM agendamentos ORDER BY id"))
                    .containsExactly("PENDENTE", "CONFIRMADO", "CANCELADO", "CONCLUIDO");
            assertThat(coluna(st, "SELECT tipo_de_residencia FROM endereco ORDER BY id")).containsExactly("CASA", "APARTAMENTO");
            // a junção renomeada pela V5 some na V15: o chef de cada linha vai para agendamentos.chefe_id
            assertThat(coluna(st, "SELECT chefe_id::text FROM agendamentos ORDER BY id"))
                    .containsOnly("00000000-0000-0000-0000-000000000002").hasSize(4);
            assertThat(coluna(st, "SELECT count(*)::text FROM information_schema.columns WHERE table_schema = 'chefhome' "
                    + "AND ((table_name = 'chefe' AND column_name = 'password') OR table_name = 'agedamento_chefe')"))
                    .containsExactly("0");
            assertThat(coluna(st, "SELECT count(*)::text FROM information_schema.tables WHERE table_schema = 'chefhome' "
                    + "AND table_name IN ('agendamento_chefe', 'agedamento_chefe')")).containsExactly("0");
        }
    }

    /**
     * V15 sobre agendamentos que já têm chefs: com vários vale o de menor id; sem nenhum (todos recusaram ou
     * desistiram) vale quem saiu por último; sem rastro nenhum o agendamento e as denúncias dele são apagados.
     */
    @Test
    void v15DeixaUmChefPorAgendamento() throws Exception {
        executarComoSuperusuario(POSTGRES, "CREATE DATABASE conversao_v15");
        Container.ExecResult setup = POSTGRES.execInContainer("psql",
                "-U", POSTGRES.getUsername(), "-d", "conversao_v15", "-v", "ON_ERROR_STOP=1",
                "-v", "senha_migracao=" + SENHA_MIGRACAO, "-v", "senha_app=" + SENHA_APP,
                "-v", "nome_banco=conversao_v15", "-f", SCRIPT_NO_CONTAINER);
        assertThat(setup.getExitCode()).as(setup.getStderr()).isZero();
        String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/conversao_v15")
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=chefhome";
        Flyway.configure().dataSource(url, "chefhome_migracao", SENHA_MIGRACAO).schemas("chefhome")
                .locations("classpath:db/migration").target("14").load().migrate();

        String ana = "00000000-0000-0000-0000-000000000001";
        String bia = "00000000-0000-0000-0000-000000000002";
        String caio = "00000000-0000-0000-0000-000000000003";
        try (Connection app = DriverManager.getConnection(url, "chefhome_app", SENHA_APP);
             Statement st = app.createStatement()) {
            st.execute("INSERT INTO usuarios (id, email, nome) VALUES ('" + ana + "', 'a@x.com', 'Ana')");
            st.execute("INSERT INTO chefe (id, email, nome) VALUES ('" + bia + "', 'b@x.com', 'Bia'), ('" + caio + "', 'c@x.com', 'Caio')");
            st.execute("INSERT INTO endereco (id, user_id, rua, tipo_de_residencia) VALUES (1, '" + ana + "', 'Rua A', 'CASA')");
            st.execute("INSERT INTO agendamentos (id, user_id, endereco_id, status) VALUES "
                    + "(1, '" + ana + "', 1, 'PENDENTE'), (2, '" + ana + "', 1, 'RECUSADO'), "
                    + "(3, '" + ana + "', 1, 'RECUSADO'), (4, '" + ana + "', 1, 'CANCELADO')");
            // 1: dois chefs; 2: ninguém, mas a Bia recusou antes do Caio desistir; 3 e 4: sem rastro de chef
            st.execute("INSERT INTO agendamento_chefe (agendamento_id, chefe_id) VALUES (1, '" + caio + "'), (1, '" + bia + "')");
            st.execute("INSERT INTO agendamento_historico (id, agendamento_id, acao, autor_id, autor_tipo, ocorrido_em) VALUES "
                    + "(1, 2, 'RECUSADO', '" + bia + "', 'CHEFE', '2026-10-01 10:00'), "
                    + "(2, 2, 'DESISTENCIA', '" + caio + "', 'CHEFE', '2026-10-01 11:00'), "
                    + "(3, 4, 'CANCELADO', '" + ana + "', 'CLIENTE', '2026-10-01 12:00')");
            st.execute("INSERT INTO denuncia (agendamento_id, chefe_id, cliente_id, motivo, descricao, status, criada_em) VALUES "
                    + "(3, '" + bia + "', '" + ana + "', 'OUTRO', 'x', 'ABERTA', '2026-10-01 10:00')");
        }

        Flyway.configure().dataSource(url, "chefhome_migracao", SENHA_MIGRACAO).schemas("chefhome")
                .locations("classpath:db/migration").load().migrate();

        try (Connection app = DriverManager.getConnection(url, "chefhome_app", SENHA_APP);
             Statement st = app.createStatement()) {
            assertThat(coluna(st, "SELECT id || ':' || chefe_id FROM agendamentos ORDER BY id"))
                    .containsExactly("1:" + bia, "2:" + caio);
            assertThat(coluna(st, "SELECT count(*)::text FROM denuncia")).containsExactly("0");
            assertThat(coluna(st, "SELECT is_nullable FROM information_schema.columns WHERE table_schema = 'chefhome' "
                    + "AND table_name = 'agendamentos' AND column_name = 'chefe_id'")).containsExactly("NO");
        }
    }

    static java.util.List<String> coluna(Statement st, String sql) throws SQLException {
        java.util.List<String> valores = new java.util.ArrayList<>();
        try (ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                valores.add(rs.getString(1));
            }
        }
        return valores;
    }

    @Test
    void senhasNaoVaoParaOLogDoServidor() throws Exception {
        rodarSetup();

        assertThat(POSTGRES.getLogs()).doesNotContain(SENHA_MIGRACAO).doesNotContain(SENHA_APP);
    }

    /**
     * Neon e outros bancos gerenciados não dão superusuário: o dono do banco tem CREATEROLE, mas não
     * pode mexer em parâmetros de log nem tem membership automática nas roles que cria.
     */
    @Test
    void setupFuncionaSemSuperusuarioComoNoNeon() throws Exception {
        try (PostgreSQLContainer gerenciado = new PostgreSQLContainer("postgres:17-alpine")) {
            gerenciado.start();
            executarComoSuperusuario(gerenciado, "CREATE ROLE dono_gerenciado LOGIN CREATEROLE NOSUPERUSER");
            executarComoSuperusuario(gerenciado, "CREATE DATABASE banco_gerenciado OWNER dono_gerenciado");
            gerenciado.copyFileToContainer(MountableFile.forHostPath(Path.of("db/setup/01_usuarios.sql")), SCRIPT_NO_CONTAINER);

            Container.ExecResult setup = gerenciado.execInContainer("psql",
                    "-U", "dono_gerenciado", "-d", "banco_gerenciado",
                    "-v", "ON_ERROR_STOP=1",
                    "-v", "senha_migracao=" + SENHA_MIGRACAO,
                    "-v", "senha_app=" + SENHA_APP,
                    "-v", "nome_banco=banco_gerenciado",
                    "-f", SCRIPT_NO_CONTAINER);
            assertThat(setup.getExitCode()).as(setup.getStderr()).isZero();

            String url = "jdbc:postgresql://" + gerenciado.getHost() + ":" + gerenciado.getMappedPort(5432)
                    + "/banco_gerenciado?currentSchema=chefhome";
            Flyway.configure().dataSource(url, "chefhome_migracao", SENHA_MIGRACAO)
                    .schemas("chefhome").locations("classpath:db/migration").load().migrate();

            try (Connection app = DriverManager.getConnection(url, "chefhome_app", SENHA_APP);
                 Statement st = app.createStatement()) {
                st.execute("INSERT INTO chefhome.usuarios (id, email, nome) VALUES (gen_random_uuid(), 'x@x.com', 'X')");
                assertSemPermissao(app, "CREATE TABLE chefhome.invasao (id int)");
                assertSemPermissao(app, "DELETE FROM chefhome.flyway_schema_history");
            }
        }
    }

    static void executarComoSuperusuario(PostgreSQLContainer container, String sql) throws Exception {
        Container.ExecResult r = container.execInContainer("psql", "-U", container.getUsername(),
                "-d", container.getDatabaseName(), "-v", "ON_ERROR_STOP=1", "-c", sql);
        assertThat(r.getExitCode()).as(r.getStderr()).isZero();
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
