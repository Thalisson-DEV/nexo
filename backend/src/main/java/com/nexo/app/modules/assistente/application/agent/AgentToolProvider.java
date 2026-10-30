package com.nexo.app.modules.assistente.application.agent;

import java.util.List;

/** Fornece ferramentas geradas dinamicamente (ex.: consultas de dominio declarativas). */
public interface AgentToolProvider {
    List<AgentTool> tools();
}
