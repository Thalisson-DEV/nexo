package com.nexo.app.modules.assistente.application.semantic;

import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.agent.ToolContext;
import com.nexo.app.modules.assistente.application.agent.ToolException;
import com.nexo.app.modules.assistente.application.agent.ToolResult;
import com.nexo.app.modules.assistente.application.results.ResultFormatter;
import com.nexo.app.modules.assistente.application.results.ResultStore;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.domain.ResultadoAssistente;
import com.nexo.app.modules.assistente.infrastructure.semantic.SemanticSqlExecutor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executa consultas da camada semantica (estruturadas ou SQL validado) e registra o resultado na conversa.
 */
@Service
public class ConsultaDadosService {

    private final ViewQueryCompiler compiler;
    private final SqlGuard sqlGuard;
    private final SemanticSqlExecutor executor;
    private final ResultStore resultStore;
    private final ResultFormatter formatter;
    private final AssistenteProperties properties;

    public ConsultaDadosService(ViewQueryCompiler compiler, SqlGuard sqlGuard, SemanticSqlExecutor executor,
            ResultStore resultStore, ResultFormatter formatter, AssistenteProperties properties) {
        this.compiler = compiler;
        this.sqlGuard = sqlGuard;
        this.executor = executor;
        this.resultStore = resultStore;
        this.formatter = formatter;
        this.properties = properties;
    }

    public ResultStore.StoredResult consultar(ViewQuery consulta, String titulo, String origem, ToolContext context) {
        ViewQueryCompiler.CompiledQuery compiled = compiler.compilar(consulta, context.security());
        TabularData data = executar(compiled.sql(), compiled.parametros(), compiled.limite(), context);
        Map<String, Object> descricao = new LinkedHashMap<>();
        descricao.put("fonte", compiled.fonte().id());
        descricao.put("consulta", consulta);
        return resultStore.registrar(context.run(), origem, ResultadoAssistente.Natureza.INTERNO,
                titulo == null || titulo.isBlank() ? compiled.fonte().nome() : titulo, data, descricao);
    }

    /** Executa sem registrar resultado (etapas intermediarias de ferramentas compostas). */
    public TabularData executarSemRegistro(ViewQuery consulta, ToolContext context) {
        ViewQueryCompiler.CompiledQuery compiled = compiler.compilar(consulta, context.security());
        return executar(compiled.sql(), compiled.parametros(), compiled.limite(), context);
    }

    /** Verifica se o usuario pode consultar a fonte (sem lancar excecao). */
    public boolean podeConsultar(String fonte, ToolContext context) {
        return compiler.autorizado(fonte, context.security());
    }

    public ToolResult consultarComoFerramenta(ViewQuery consulta, String titulo, String origem, ToolContext context) {
        ResultStore.StoredResult stored = consultar(consulta, titulo, origem, context);
        return formatter.toToolResult(stored, properties.getAgent().getToolResultMaxChars(),
                Map.of("fonte", consulta.fonte()), null);
    }

    public ToolResult sql(String sql, String titulo, ToolContext context) {
        SqlGuard.ValidatedSql validated;
        try {
            validated = sqlGuard.validar(sql, context.security());
        } catch (SqlGuard.SqlRejeitadoException rejected) {
            throw new ToolException(rejected.getMessage());
        }
        TabularData data = executar(validated.sql(), List.of(), properties.getSql().getMaxRows(), context);
        Map<String, Object> descricao = new LinkedHashMap<>();
        descricao.put("sql", validated.sql());
        descricao.put("fontes", validated.views());
        ResultStore.StoredResult stored = resultStore.registrar(context.run(), "consultar_sql",
                ResultadoAssistente.Natureza.INTERNO, titulo == null || titulo.isBlank() ? "Consulta SQL" : titulo,
                data, descricao);
        return formatter.toToolResult(stored, properties.getAgent().getToolResultMaxChars(),
                Map.of("fontes", validated.views()), null);
    }

    public TabularData executar(String sql, List<Object> parametros, int limite, ToolContext context) {
        try {
            return executor.executar(sql, parametros, context.security(), limite);
        } catch (SemanticSqlExecutor.ConsultaAnaliticaException exception) {
            throw new ToolException(exception.getMessage());
        }
    }

    public boolean disponivel() {
        return properties.getSql().isEnabled();
    }
}
