package com.nexo.app.modules.assistente.application.agent;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Registro de ferramentas (equivalente ao construct_tools do Onyx). A lista enviada ao modelo em cada
 * execucao contem somente ferramentas que o usuario pode usar e que estao operacionais.
 */
@Component
public class ToolRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolRegistry.class);
    private final Map<String, AgentTool> tools;

    public ToolRegistry(ObjectProvider<AgentTool> beans, ObjectProvider<AgentToolProvider> providers) {
        Map<String, AgentTool> indexed = new LinkedHashMap<>();
        List<AgentTool> all = new ArrayList<>(beans.orderedStream().toList());
        providers.orderedStream().forEach(provider -> all.addAll(provider.tools()));
        for (AgentTool tool : all) {
            if (!tool.name().matches("[a-z][a-z0-9_]{2,63}")) {
                throw new IllegalStateException("Nome de ferramenta invalido: " + tool.name());
            }
            if (indexed.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalStateException("Ferramenta duplicada: " + tool.name());
            }
        }
        this.tools = Collections.unmodifiableMap(indexed);
    }

    public Optional<AgentTool> buscar(String name) {
        return Optional.ofNullable(name == null ? null : tools.get(name.trim()));
    }

    public Collection<AgentTool> todas() {
        return tools.values();
    }

    public List<AgentTool> autorizadas(SemanticQueryContext contexto) {
        return tools.values().stream().filter(tool -> autorizada(tool, contexto)).toList();
    }

    public boolean autorizada(AgentTool tool, SemanticQueryContext contexto) {
        Set<String> exigidas = tool.requiredAuthorities();
        return exigidas.isEmpty() || exigidas.stream().anyMatch(contexto::possuiAutoridade);
    }

    public List<AgentTool> disponiveis(AgentRunContext contexto) {
        List<AgentTool> disponiveis = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            if (!tool.oferecidaNoChat() || !autorizada(tool, contexto.security())) continue;
            try {
                if (tool.isAvailable(contexto)) disponiveis.add(tool);
            } catch (RuntimeException exception) {
                LOGGER.warn("Ferramenta {} indisponivel: {}", tool.name(), exception.getClass().getSimpleName());
            }
        }
        return disponiveis;
    }
}
