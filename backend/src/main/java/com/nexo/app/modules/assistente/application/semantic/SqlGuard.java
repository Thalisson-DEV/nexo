package com.nexo.app.modules.assistente.application.semantic;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validacao do SQL analitico gerado pelo agente. Defesa em camadas:
 * <ol>
 *   <li>exatamente um comando, do tipo SELECT (inclusive WITH), analisado pelo JSqlParser;</li>
 *   <li>varredura lexica (fora de literais e comentarios) contra palavras de escrita/controle,
 *       SELECT INTO, FOR UPDATE e funcoes perigosas ou de sistema;</li>
 *   <li>toda relacao referenciada precisa ser uma view publicada da camada semantica e o usuario precisa
 *       da permissao do Nexo correspondente a cada uma.</li>
 * </ol>
 * A execucao ainda ocorre em transacao somente leitura, com timeout, limite de linhas, search_path
 * restrito e role opcional sem privilegios de escrita.
 */
@Component
public class SqlGuard {

    public static final int MAX_SQL_LENGTH = 12_000;

    private static final Set<String> PALAVRAS_PROIBIDAS = Set.of(
            "INSERT", "UPDATE", "DELETE", "MERGE", "UPSERT", "DROP", "ALTER", "CREATE", "TRUNCATE", "GRANT",
            "REVOKE", "COPY", "CALL", "DO", "EXECUTE", "EXEC", "PREPARE", "DEALLOCATE", "SET", "RESET", "LOCK",
            "VACUUM", "ANALYZE", "REFRESH", "LISTEN", "NOTIFY", "UNLISTEN", "DISCARD", "COMMENT", "SECURITY",
            "REINDEX", "CLUSTER", "CHECKPOINT", "LOAD", "IMPORT", "INTO", "RETURNING", "BEGIN", "COMMIT",
            "ROLLBACK", "SAVEPOINT", "RELEASE", "START", "TRANSACTION", "OWNER", "EXPLAIN");

    private static final Set<String> FUNCOES_PROIBIDAS = Set.of(
            "set_config", "current_setting", "query_to_xml", "query_to_xml_and_xmlschema", "query_to_xmlschema",
            "table_to_xml", "table_to_xml_and_xmlschema", "cursor_to_xml", "schema_to_xml", "database_to_xml",
            "xpath", "xpath_exists", "nextval", "setval", "currval", "lastval", "txid_current",
            "txid_current_if_assigned", "inet_server_addr", "inet_server_port", "inet_client_addr",
            "inet_client_port", "version", "has_table_privilege", "has_schema_privilege", "has_database_privilege",
            "has_function_privilege", "has_column_privilege", "row_security_active", "obj_description",
            "col_description", "shobj_description", "format_type", "to_regclass", "to_regproc", "to_regtype",
            "binary_upgrade", "make_interval_unsafe");

    private static final List<String> PREFIXOS_PROIBIDOS = List.of("pg_", "lo_", "dblink", "_pg", "file_", "gp_");

    private static final Pattern CHAMADA_FUNCAO = Pattern.compile("([A-Za-z_][A-Za-z0-9_$.]*)\\s*\\(");
    private static final Pattern FOR_LOCK = Pattern.compile("\\bFOR\\s+(UPDATE|SHARE|NO\\s+KEY|KEY\\s+SHARE)\\b");
    private static final Pattern PALAVRA = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    private final SemanticCatalog catalog;

    public SqlGuard(SemanticCatalog catalog) {
        this.catalog = catalog;
    }

    public ValidatedSql validar(String sql, SemanticQueryContext contexto) {
        if (sql == null || sql.isBlank()) throw new SqlRejeitadoException("Informe a consulta SQL.");
        String limpo = sql.trim();
        while (limpo.endsWith(";")) limpo = limpo.substring(0, limpo.length() - 1).trim();
        if (limpo.length() > MAX_SQL_LENGTH) {
            throw new SqlRejeitadoException("Consulta muito longa (maximo " + MAX_SQL_LENGTH + " caracteres).");
        }

        String semLiterais = removerLiteraisEComentarios(limpo);
        if (semLiterais.contains(";")) {
            throw new SqlRejeitadoException("Envie um unico comando SELECT, sem ';' intermediario.");
        }
        verificarLexico(semLiterais);

        Statement statement;
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(limpo);
            if (statements.size() != 1) throw new SqlRejeitadoException("Envie exatamente um comando SELECT.");
            statement = statements.get(0);
        } catch (JSQLParserException exception) {
            throw new SqlRejeitadoException("SQL invalido ou nao suportado: "
                    + primeiraLinha(exception.getMessage()) + ". Simplifique a consulta (PostgreSQL).");
        }
        if (!(statement instanceof Select)) {
            throw new SqlRejeitadoException("Somente consultas SELECT sao permitidas.");
        }

        Set<String> tabelas;
        try {
            tabelas = new TablesNamesFinder().getTables(statement);
        } catch (RuntimeException exception) {
            throw new SqlRejeitadoException("Nao foi possivel identificar as fontes consultadas.");
        }
        if (tabelas.isEmpty()) {
            throw new SqlRejeitadoException("A consulta precisa ler ao menos uma view da camada semantica (schema copiloto).");
        }

        Set<String> views = new LinkedHashSet<>();
        for (String tabela : tabelas) {
            String nome = tabela.replace("\"", "").trim().toLowerCase(Locale.ROOT);
            String[] partes = nome.split("\\.");
            if (partes.length > 2 || (partes.length == 2 && !"copiloto".equals(partes[0]))) {
                throw new SqlRejeitadoException("Fonte '" + tabela + "' nao permitida. Use somente as views do schema copiloto "
                        + "(descubra-as com listar_fontes_dados).");
            }
            String view = partes[partes.length - 1];
            SemanticCatalog.Fonte fonte = catalog.fonte(view).orElseThrow(() -> new SqlRejeitadoException(
                    "Fonte '" + view + "' nao existe na camada semantica. Use listar_fontes_dados."));
            if (!catalog.autorizado(fonte, contexto)) {
                throw new SqlAcessoNegadoException(fonte.nome());
            }
            views.add(fonte.id());
        }
        return new ValidatedSql(limpo, List.copyOf(views));
    }

    private void verificarLexico(String semLiterais) {
        String maiusculo = semLiterais.toUpperCase(Locale.ROOT);
        Matcher palavras = PALAVRA.matcher(maiusculo);
        while (palavras.find()) {
            String palavra = palavras.group();
            if (PALAVRAS_PROIBIDAS.contains(palavra)) {
                throw new SqlRejeitadoException("Comando '" + palavra + "' nao permitido: o acesso e somente leitura por SELECT.");
            }
        }
        String primeira = maiusculo.stripLeading();
        if (!(primeira.startsWith("SELECT") || primeira.startsWith("WITH") || primeira.startsWith("("))) {
            throw new SqlRejeitadoException("A consulta deve comecar com SELECT ou WITH.");
        }
        if (FOR_LOCK.matcher(maiusculo).find()) {
            throw new SqlRejeitadoException("Bloqueio de linhas (FOR UPDATE/SHARE) nao e permitido.");
        }
        Matcher funcoes = CHAMADA_FUNCAO.matcher(semLiterais);
        while (funcoes.find()) {
            String nome = funcoes.group(1).toLowerCase(Locale.ROOT);
            if (nome.contains(".")) {
                throw new SqlRejeitadoException("Chamadas de funcao qualificadas por schema nao sao permitidas: " + nome);
            }
            if (FUNCOES_PROIBIDAS.contains(nome) || PREFIXOS_PROIBIDOS.stream().anyMatch(nome::startsWith)) {
                throw new SqlRejeitadoException("Funcao '" + nome + "' nao permitida no SQL analitico.");
            }
        }
        if (semLiterais.matches("(?s).*\\bpg_[a-z_]+\\b.*")) {
            throw new SqlRejeitadoException("Objetos de sistema (pg_*) nao podem ser consultados.");
        }
    }

    /** Remove literais de texto, dollar-quoting e comentarios; identificadores entre aspas viram placeholder. */
    static String removerLiteraisEComentarios(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                while (i < n && sql.charAt(i) != '\n') i++;
                out.append(' ');
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                out.append(' ');
            } else if (c == '\'') {
                i++;
                while (i < n) {
                    if (sql.charAt(i) == '\'') {
                        if (i + 1 < n && sql.charAt(i + 1) == '\'') {
                            i += 2;
                            continue;
                        }
                        break;
                    }
                    if (sql.charAt(i) == '\\' && i + 1 < n) i++;
                    i++;
                }
                i++;
                out.append(" '' ");
            } else if (c == '"') {
                int end = sql.indexOf('"', i + 1);
                String identificador = end < 0 ? "" : sql.substring(i + 1, end);
                i = end < 0 ? n : end + 1;
                out.append(' ').append(identificador.replaceAll("[^A-Za-z0-9_]", "_")).append(' ');
            } else if (c == '$') {
                Matcher tag = Pattern.compile("^\\$[A-Za-z_]*\\$").matcher(sql.substring(i));
                if (tag.find()) {
                    String delimitador = tag.group();
                    int end = sql.indexOf(delimitador, i + delimitador.length());
                    i = end < 0 ? n : end + delimitador.length();
                    out.append(" '' ");
                } else {
                    out.append(c);
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static String primeiraLinha(String texto) {
        if (texto == null) return "erro de sintaxe";
        String linha = texto.split("\\R", 2)[0].trim();
        return linha.length() > 200 ? linha.substring(0, 200) : linha;
    }

    public record ValidatedSql(String sql, List<String> views) {
    }

    public static class SqlRejeitadoException extends RuntimeException {
        public SqlRejeitadoException(String message) {
            super(message);
        }
    }

    public static class SqlAcessoNegadoException extends SqlRejeitadoException {
        public SqlAcessoNegadoException(String fonte) {
            super("Seu perfil nao tem acesso a '" + fonte + "'. Responda sem esses dados e explique a limitacao em linguagem de negocio.");
        }
    }
}
