package com.nexo.app.modules.assistente.application.results;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.agent.AgentRunContext;
import com.nexo.app.modules.assistente.application.agent.ToolException;
import com.nexo.app.modules.assistente.domain.ResultadoAssistente;
import com.nexo.app.modules.assistente.infrastructure.persistence.ResultadoAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.ResumoResultadoAssistente;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Registro de resultados tabulares da conversa (r1, r2...). E o que permite ao agente dizer "gere um
 * grafico dessas movimentacoes" sem reconsultar e sem copiar numeros: artefatos e relatorios referenciam
 * o codigo do resultado e o backend resolve os valores.
 */
@Service
public class ResultStore {

    /** Linhas persistidas por resultado; o total real continua informado em {@code totalLinhas}. */
    public static final int MAX_LINHAS_PERSISTIDAS = 5_000;
    private static final int MAX_JSON_BYTES = 4 * 1024 * 1024;
    private static final TypeReference<List<TabularData.Column>> COLUMNS = new TypeReference<>() { };
    private static final TypeReference<List<List<Object>>> ROWS = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> CONSULTA = new TypeReference<>() { };

    private final ResultadoAssistenteRepository repository;
    private final ObjectMapper objectMapper;

    public ResultStore(ResultadoAssistenteRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoredResult registrar(AgentRunContext context, String origem, ResultadoAssistente.Natureza natureza,
            String titulo, TabularData data, Map<String, Object> consulta) {
        List<List<Object>> rows = data.rows();
        boolean truncado = data.truncated();
        if (rows.size() > MAX_LINHAS_PERSISTIDAS) {
            rows = rows.subList(0, MAX_LINHAS_PERSISTIDAS);
            truncado = true;
        }
        String linhasJson = write(rows);
        while (linhasJson.length() > MAX_JSON_BYTES && rows.size() > 10) {
            rows = rows.subList(0, rows.size() / 2);
            truncado = true;
            linhasJson = write(rows);
        }
        String codigo = context.novoCodigoResultado();
        int total = Math.max(data.totalRows(), rows.size());
        ResultadoAssistente entity = repository.save(new ResultadoAssistente(
                context.conversaId(), context.mensagemAssistenteId(), codigo, origem, natureza, titulo,
                write(data.columns()), linhasJson, total, truncado, consulta == null ? null : write(consulta)));
        context.registrarResultadoDoTurno(codigo);
        TabularData stored = new TabularData(data.columns(), rows, total, truncado);
        return new StoredResult(entity.getCodigo(), entity.getTitulo(), natureza, stored, origem,
                consulta == null ? Map.of() : consulta);
    }

    @Transactional(readOnly = true)
    public Optional<StoredResult> carregar(UUID conversaId, String codigo) {
        if (codigo == null || codigo.isBlank()) return Optional.empty();
        return repository.findByConversaIdAndCodigo(conversaId, codigo.trim().toLowerCase(Locale.ROOT))
                .map(this::toStored);
    }

    public StoredResult exigir(UUID conversaId, String codigo) {
        return carregar(conversaId, codigo).orElseThrow(() -> new ToolException(
                "Resultado '" + codigo + "' nao existe nesta conversa. Use um codigo retornado por uma ferramenta (ex.: r1)."));
    }

    @Transactional(readOnly = true)
    public List<ResumoResultadoAssistente> listarResumos(UUID conversaId) {
        return repository.listarResumos(conversaId);
    }

    @Transactional(readOnly = true)
    public int contar(UUID conversaId) {
        return Math.toIntExact(repository.countByConversaId(conversaId));
    }

    public List<TabularData.Column> colunas(String colunasJson) {
        try {
            return objectMapper.readValue(colunasJson, COLUMNS);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private StoredResult toStored(ResultadoAssistente entity) {
        try {
            List<TabularData.Column> columns = objectMapper.readValue(entity.getColunasJson(), COLUMNS);
            List<List<Object>> rows = objectMapper.readValue(entity.getLinhasJson(), ROWS);
            Map<String, Object> consulta = entity.getConsultaJson() == null || entity.getConsultaJson().isBlank()
                    ? Map.of() : objectMapper.readValue(entity.getConsultaJson(), CONSULTA);
            return new StoredResult(entity.getCodigo(), entity.getTitulo(), entity.getNatureza(),
                    new TabularData(columns, rows, entity.getTotalLinhas(), entity.isTruncado()), entity.getOrigem(),
                    consulta);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Resultado persistido invalido: " + entity.getCodigo(), exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Falha ao serializar resultado", exception);
        }
    }

    /**
     * Resultado carregado. {@code origem} e a ferramenta que o produziu e {@code consulta} os parametros
     * usados (fonte, filtros, periodo), exibidos como procedencia em relatorios e artefatos.
     */
    public record StoredResult(String codigo, String titulo, ResultadoAssistente.Natureza natureza, TabularData data,
            String origem, Map<String, Object> consulta) {
    }
}
