package com.nexo.app.modules.assistente.infrastructure.semantic;

import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.application.semantic.SemanticCatalog;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Executa consultas analiticas contra a camada semantica em modo estritamente somente leitura:
 * transacao READ ONLY, statement_timeout, lock_timeout, search_path restrito ao schema copiloto,
 * role opcional sem privilegio de escrita, limite de linhas e concorrencia limitada. O escopo por
 * usuario das views (meus chamados, minhas notificacoes) e definido aqui, nunca pelo SQL do agente.
 */
@Component
public class SemanticSqlExecutor {

    private final DataSource dataSource;
    private final AssistenteProperties properties;
    private final SemanticCatalog catalog;
    private final Semaphore concorrencia;

    public SemanticSqlExecutor(DataSource dataSource, AssistenteProperties properties, SemanticCatalog catalog) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.catalog = catalog;
        this.concorrencia = new Semaphore(Math.max(1, properties.getSql().getMaxConcurrentQueries()), true);
    }

    public TabularData executar(String sql, List<Object> parametros, SemanticQueryContext contexto, int maxLinhas) {
        AssistenteProperties.Sql config = properties.getSql();
        int limite = Math.max(1, Math.min(maxLinhas, config.getMaxRows()));
        boolean adquirido;
        try {
            adquirido = concorrencia.tryAcquire(20, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ConsultaAnaliticaException("Consulta interrompida.");
        }
        if (!adquirido) throw new ConsultaAnaliticaException("Muitas consultas analiticas simultaneas. Tente novamente.");
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres")) {
                throw new ConsultaAnaliticaException("A camada semantica SQL exige PostgreSQL.");
            }
            boolean autoCommitOriginal = connection.getAutoCommit();
            connection.setAutoCommit(false);
            connection.setReadOnly(true);
            try {
                prepararSessao(connection, contexto, config);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    for (int i = 0; i < parametros.size(); i++) statement.setObject(i + 1, parametros.get(i));
                    statement.setMaxRows(limite + 1);
                    statement.setFetchSize(Math.min(limite + 1, 500));
                    statement.setQueryTimeout(Math.max(1, (config.getStatementTimeoutMs() / 1000) + 5));
                    try (ResultSet resultSet = statement.executeQuery()) {
                        return ler(resultSet, limite);
                    }
                }
            } finally {
                connection.rollback();
                connection.setReadOnly(false);
                connection.setAutoCommit(autoCommitOriginal);
            }
        } catch (SQLException exception) {
            throw traduzir(exception, config);
        } finally {
            concorrencia.release();
        }
    }

    private void prepararSessao(Connection connection, SemanticQueryContext contexto, AssistenteProperties.Sql config)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TRANSACTION READ ONLY");
            statement.execute("SET LOCAL statement_timeout = " + Math.max(1000, config.getStatementTimeoutMs()));
            statement.execute("SET LOCAL lock_timeout = 3000");
            statement.execute("SET LOCAL search_path = " + identificador(config.getSchema()) + ", pg_catalog");
            String role = config.getRole();
            if (role != null && !role.isBlank()) statement.execute("SET LOCAL ROLE " + identificador(role));
        }
        try (PreparedStatement escopo = connection.prepareStatement(
                "SELECT set_config('copiloto.usuario_id', ?, true), set_config('copiloto.escopo_chamados', ?, true)")) {
            escopo.setString(1, contexto.usuarioId() == null ? "" : contexto.usuarioId().toString());
            escopo.setString(2, catalog.veTodosChamados(contexto) ? "TODOS" : "PROPRIO");
            escopo.execute();
        }
    }

    private static TabularData ler(ResultSet resultSet, int limite) throws SQLException {
        ResultSetMetaData meta = resultSet.getMetaData();
        int colunas = meta.getColumnCount();
        List<TabularData.Column> columns = new ArrayList<>();
        for (int i = 1; i <= colunas; i++) columns.add(new TabularData.Column(meta.getColumnLabel(i), tipo(meta.getColumnType(i))));
        List<List<Object>> rows = new ArrayList<>();
        boolean truncado = false;
        while (resultSet.next()) {
            if (rows.size() >= limite) {
                truncado = true;
                break;
            }
            List<Object> row = new ArrayList<>(colunas);
            for (int i = 1; i <= colunas; i++) row.add(TabularData.normalize(resultSet.getObject(i)));
            rows.add(row);
        }
        return new TabularData(columns, rows, rows.size(), truncado);
    }

    private static TabularData.ColumnType tipo(int sqlType) {
        return switch (sqlType) {
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT, Types.BIGINT -> TabularData.ColumnType.INTEIRO;
            case Types.NUMERIC, Types.DECIMAL, Types.DOUBLE, Types.FLOAT, Types.REAL -> TabularData.ColumnType.NUMERO;
            case Types.DATE -> TabularData.ColumnType.DATA;
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> TabularData.ColumnType.DATA_HORA;
            case Types.BOOLEAN, Types.BIT -> TabularData.ColumnType.BOOLEANO;
            case Types.OTHER -> TabularData.ColumnType.TEXTO;
            default -> TabularData.ColumnType.TEXTO;
        };
    }

    private static ConsultaAnaliticaException traduzir(SQLException exception, AssistenteProperties.Sql config) {
        String state = exception.getSQLState() == null ? "" : exception.getSQLState();
        if ("57014".equals(state)) {
            return new ConsultaAnaliticaException("A consulta excedeu o tempo limite de "
                    + (config.getStatementTimeoutMs() / 1000) + "s. Agregue mais, filtre o periodo ou reduza as juncoes.");
        }
        if (state.startsWith("25") || "42501".equals(state)) {
            return new ConsultaAnaliticaException("Operacao nao permitida: o acesso analitico e somente leitura.");
        }
        String mensagem = exception.getMessage() == null ? "erro de SQL" : exception.getMessage();
        mensagem = mensagem.replaceAll("\\s+", " ");
        int posicao = mensagem.indexOf(" Position:");
        if (posicao > 0) mensagem = mensagem.substring(0, posicao);
        if (mensagem.length() > 400) mensagem = mensagem.substring(0, 400) + "...";
        return new ConsultaAnaliticaException("Erro ao executar a consulta: " + mensagem);
    }

    private static String identificador(String valor) {
        if (valor == null || !valor.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException("Identificador de configuracao SQL invalido");
        }
        return "\"" + valor + "\"";
    }

    public static class ConsultaAnaliticaException extends RuntimeException {
        public ConsultaAnaliticaException(String message) {
            super(message);
        }
    }
}
