package com.nexo.app.modules.assistente.application.mcp;

import com.nexo.app.modules.assistente.application.agent.AgentRun;
import com.nexo.app.modules.assistente.application.agent.AgentRunContext;
import com.nexo.app.modules.assistente.application.agent.AgentTool;
import com.nexo.app.modules.assistente.application.agent.ToolKind;
import com.nexo.app.modules.assistente.application.agent.ToolRegistry;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ferramentas do Copiloto que uma IA externa enxerga pelo MCP. E o mesmo registro do chat, filtrado
 * pelas permissoes do usuario da conexao; ferramentas que cadastram ou alteram registros so aparecem
 * quando a conexao permite escrita. Toda ferramenta de escrita registrada no Copiloto entra aqui
 * automaticamente.
 *
 * <p>Ficam de fora as que so fazem sentido dentro da tela do Nexo (navegar, artefatos e arquivos da
 * conversa, relatorios e exportacoes para baixar) e as que a propria IA externa ja tem (pesquisa web e
 * leitura de paginas).</p>
 */
@Component
public class McpCatalogo {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpCatalogo.class);
    private static final Set<ToolKind> SO_NA_TELA = EnumSet.of(ToolKind.NAVIGATION, ToolKind.ARTIFACT, ToolKind.FILE,
            ToolKind.REPORT, ToolKind.EXPORT, ToolKind.WEB_SEARCH, ToolKind.OPEN_URL);

    private final ToolRegistry registry;

    public McpCatalogo(ToolRegistry registry) {
        this.registry = registry;
    }

    /** Cadastra, altera ou dispara algo no Nexo. */
    public static boolean escreve(AgentTool tool) {
        return tool.kind() == ToolKind.WRITE || tool.riskLevel().requiresConfirmation();
    }

    public static boolean exposta(AgentTool tool) {
        return !SO_NA_TELA.contains(tool.kind());
    }

    public List<AgentTool> disponiveis(SemanticQueryContext seguranca, boolean permiteEscrita) {
        AgentRunContext contexto = contextoDeConsulta(seguranca);
        List<AgentTool> lista = new ArrayList<>();
        for (AgentTool tool : registry.autorizadas(seguranca)) {
            if (!exposta(tool) || (escreve(tool) && !permiteEscrita)) continue;
            if (operacional(tool, contexto)) lista.add(tool);
        }
        return lista;
    }

    public Optional<AgentTool> buscar(String nome, SemanticQueryContext seguranca, boolean permiteEscrita) {
        return registry.buscar(nome)
                .filter(McpCatalogo::exposta)
                .filter(tool -> registry.autorizada(tool, seguranca))
                .filter(tool -> permiteEscrita || !escreve(tool));
    }

    /** A ferramenta existe, mas a conexao nao pode usa-la (sem permissao ou so leitura). */
    public boolean existe(String nome) {
        return registry.buscar(nome).filter(McpCatalogo::exposta).isPresent();
    }

    private static boolean operacional(AgentTool tool, AgentRunContext contexto) {
        try {
            return tool.isAvailable(contexto);
        } catch (RuntimeException exception) {
            LOGGER.warn("Ferramenta {} indisponivel para o MCP: {}", tool.name(), exception.getClass().getSimpleName());
            return false;
        }
    }

    /** Contexto descartavel so para perguntar as ferramentas se estao operacionais. */
    private static AgentRunContext contextoDeConsulta(SemanticQueryContext seguranca) {
        UUID usuario = seguranca.usuarioId() == null ? new UUID(0, 0) : seguranca.usuarioId();
        AgentRun run = new AgentRun(UUID.randomUUID(), usuario, null, UUID.randomUUID(), null, pacote -> "");
        return new AgentRunContext(run, seguranca, null, null, List.of(), Duration.ofSeconds(5), 0, 0, 0);
    }
}
