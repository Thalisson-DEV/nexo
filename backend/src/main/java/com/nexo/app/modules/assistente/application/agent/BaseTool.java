package com.nexo.app.modules.assistente.application.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Set;

/** Base para ferramentas com metadados fixos. */
public abstract class BaseTool implements AgentTool {

    private final String name;
    private final String displayName;
    private final String description;
    private final JsonNode inputSchema;
    private final ToolKind kind;
    private final Set<String> authorities;
    private final Duration timeout;

    protected BaseTool(String name, String displayName, String description, JsonNode inputSchema, ToolKind kind,
            Set<String> authorities, Duration timeout) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.inputSchema = inputSchema;
        this.kind = kind;
        this.authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
        this.timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public JsonNode inputSchema() {
        return inputSchema;
    }

    @Override
    public ToolKind kind() {
        return kind;
    }

    @Override
    public Set<String> requiredAuthorities() {
        return authorities;
    }

    @Override
    public Duration timeout() {
        return timeout;
    }
}
