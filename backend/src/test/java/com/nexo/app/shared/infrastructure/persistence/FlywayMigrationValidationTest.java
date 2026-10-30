package com.nexo.app.shared.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;

class FlywayMigrationValidationTest {

    @Test
    void todasAsMigracoesPossuemVersoesUnicasEValidas() {
        assertThatNoException().isThrownBy(() -> {
            Flyway flyway = Flyway.configure()
                    .dataSource("jdbc:h2:mem:flyway_test;DB_CLOSE_DELAY=-1", "sa", "")
                    .locations("classpath:db/migration")
                    .load();

            MigrationInfo[] all = flyway.info().all();
            assertThat(all).isNotEmpty();
        });
    }
}
