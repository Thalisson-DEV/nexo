package com.nexo.app.modules.automacao.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Silêncio por assunto ("não repetir o mesmo aviso por N minutos"). Tabela técnica de
 * chave composta, acessada por SQL direto: não há entidade de negócio aqui.
 */
@Repository
public class SilencioAutomacaoRepository {

    private final JdbcTemplate jdbc;

    public SilencioAutomacaoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OffsetDateTime ultimoEnvio(UUID automacao, String noId, String chave) {
        return jdbc.query("SELECT ultimo_envio FROM automacao_silencios WHERE id_automacao = ? AND no_id = ? AND chave = ?",
                rs -> rs.next() ? rs.getObject(1, OffsetDateTime.class) : null, automacao, noId, chave);
    }

    public void registrar(UUID automacao, String noId, String chave, OffsetDateTime momento) {
        jdbc.update("""
                INSERT INTO automacao_silencios (id_automacao, no_id, chave, ultimo_envio) VALUES (?, ?, ?, ?)
                ON CONFLICT (id_automacao, no_id, chave) DO UPDATE SET ultimo_envio = EXCLUDED.ultimo_envio
                """, automacao, noId, chave, momento);
    }
}
