package com.nexo.app.modules.telemetria.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Gravação em lote dos eventos de telemetria.
 *
 * <p>JDBC direto, sem JPA, de propósito: é registro técnico de alto volume e somente
 * inserção, não entidade de negócio. Passar pelo {@code AuditListener} geraria uma
 * linha de auditoria por evento sem nenhum valor (decisão registrada em
 * docs/mobile/DECISOES.md).</p>
 *
 * @author <b>Thalisson</b>
 */
@Repository
@RequiredArgsConstructor
public class TelemetriaMobileRepository {

    private final JdbcTemplate jdbc;

    public record Linha(OffsetDateTime ocorridoEm, String tipo, String idAparelho, String versaoApp, String dadosJson) {
    }

    public int inserir(List<Linha> linhas) {
        if (linhas.isEmpty()) {
            return 0;
        }
        jdbc.batchUpdate(
                "INSERT INTO telemetria_mobile (ocorrido_em, tipo, id_aparelho, versao_app, dados) VALUES (?, ?, ?, ?, CAST(? AS JSON))",
                linhas, linhas.size(), (ps, l) -> {
                    ps.setTimestamp(1, Timestamp.from(l.ocorridoEm().toInstant()));
                    ps.setString(2, l.tipo());
                    ps.setString(3, l.idAparelho());
                    ps.setString(4, l.versaoApp());
                    ps.setString(5, l.dadosJson());
                });
        return linhas.size();
    }

    /** Retenção: remove eventos recebidos antes do limite. Tabela técnica, não de negócio. */
    public int expurgarAntesDe(OffsetDateTime limite) {
        return jdbc.update("DELETE FROM telemetria_mobile WHERE recebido_em < ?", Timestamp.from(limite.toInstant()));
    }
}
