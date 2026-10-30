package com.nexo.app.modules.assistente.application.mcp;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import com.nexo.app.shared.infrastructure.security.AcessoExterno;

import java.util.Objects;

/**
 * Quem esta chamando o MCP: a conexao (e o que ela permite) e a identidade/permissoes do usuario,
 * capturadas na thread da requisicao.
 */
public record SessaoMcp(AcessoExterno acesso, SemanticQueryContext seguranca, String nomeUsuario) {

    public SessaoMcp {
        Objects.requireNonNull(acesso, "acesso");
        Objects.requireNonNull(seguranca, "seguranca");
    }

    public boolean permiteEscrita() {
        return acesso.permiteEscrita();
    }
}
