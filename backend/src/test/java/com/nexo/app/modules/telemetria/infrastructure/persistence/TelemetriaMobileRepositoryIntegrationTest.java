package com.nexo.app.modules.telemetria.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TelemetriaMobileRepositoryIntegrationTest {

    @Autowired TelemetriaMobileRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void criarTabelaEquivalenteNoH2() {
        // Testes rodam em H2 com Flyway desligado: tabela equivalente (JSONB → JSON).
        jdbc.execute("DROP TABLE IF EXISTS telemetria_mobile");
        jdbc.execute("""
                CREATE TABLE telemetria_mobile (
                    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                    recebido_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                    ocorrido_em TIMESTAMP WITH TIME ZONE NOT NULL,
                    tipo VARCHAR(40) NOT NULL,
                    id_aparelho VARCHAR(64),
                    versao_app VARCHAR(40),
                    dados JSON NOT NULL)""");
    }

    @Test
    void insereEmLoteEExpurgaPelaDataDeRecebimento() {
        var agora = OffsetDateTime.now(ZoneOffset.UTC);
        int n = repository.inserir(List.of(
                new TelemetriaMobileRepository.Linha(agora, "app.abertura", "a1", "v1", "{\"ms\":120}"),
                new TelemetriaMobileRepository.Linha(agora, "sync.falha", null, null, "{}")));
        assertThat(n).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM telemetria_mobile", Integer.class)).isEqualTo(2);

        jdbc.update("UPDATE telemetria_mobile SET recebido_em = ? WHERE tipo = 'sync.falha'", agora.minusDays(91));
        assertThat(repository.expurgarAntesDe(agora.minusDays(90))).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT tipo FROM telemetria_mobile", String.class)).containsExactly("app.abertura");
    }

    @Test
    void loteVazioNaoTocaNoBanco() {
        assertThat(repository.inserir(List.of())).isZero();
    }

    @Test
    void migracaoEhAditivaSemDadoPessoalEComRollbackDocumentado() throws IOException {
        String sql;
        try (var in = new ClassPathResource("db/migration/V166__Telemetria_Mobile.sql").getInputStream()) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
        }
        String ddl = sql.lines().filter(l -> !l.stripLeading().startsWith("--")).reduce("", (a, b) -> a + b + "\n");
        assertThat(ddl).contains("CREATE TABLE TELEMETRIA_MOBILE");
        assertThat(ddl).doesNotContain("ALTER TABLE").doesNotContain("DROP ").doesNotContain("DELETE ");
        assertThat(ddl).doesNotContain("ID_USUARIO").doesNotContain("ID_EQUIPE").doesNotContain("LOGIN").doesNotContain("NOME");
        assertThat(sql).contains("ROLLBACK: DROP TABLE TELEMETRIA_MOBILE;");
    }
}
