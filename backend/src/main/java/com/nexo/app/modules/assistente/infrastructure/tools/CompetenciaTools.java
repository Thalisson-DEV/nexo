package com.nexo.app.modules.assistente.infrastructure.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.app.modules.assistente.application.agent.*;
import com.nexo.app.modules.assistente.application.results.ResultFormatter;
import com.nexo.app.modules.assistente.application.skills.SkillCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Carrega o roteiro completo de uma competencia (skill) listada no prompt de sistema. */
@Configuration(proxyBeanMethods = false)
public class CompetenciaTools {

    @Bean
    AgentTool usarCompetenciaTool(SkillCatalog catalogo, ResultFormatter formatter) {
        return new BaseTool("usar_competencia", "Usar competência",
                "Carrega o roteiro detalhado de uma competencia de analise listada em 'Competencias disponiveis' "
                        + "(fontes certas, passos, cuidados e formato de entrega). Use no inicio de tarefas complexas que "
                        + "correspondam a uma competencia; depois siga o roteiro adaptando ao pedido.",
                JsonSchemas.object()
                        .required("nome", JsonSchemas.string("Nome da competencia, ex.: analise-consumo-materiais"))
                        .build(),
                ToolKind.SKILL, Set.of(), Duration.ofSeconds(5)) {
            @Override
            public boolean isAvailable(AgentRunContext context) {
                return !catalogo.disponiveis(context.security()).isEmpty();
            }

            @Override
            public String progressLabel(JsonNode arguments) {
                return catalogo.buscar(ToolArgs.text(arguments, "nome"))
                        .map(skill -> "Aplicando roteiro: " + skill.titulo())
                        .orElse("Carregando roteiro de análise");
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                String nome = ToolArgs.requiredText(arguments, "nome");
                SkillCatalog.Skill skill = catalogo.buscar(nome)
                        .filter(item -> item.permitida(context.security()))
                        .orElseThrow(() -> new ToolException("Competencia '" + nome + "' nao existe ou nao esta disponivel. "
                                + "Disponiveis: " + String.join(", ", catalogo.disponiveis(context.security()).stream()
                                .map(SkillCatalog.Skill::nome).toList())));
                Map<String, Object> resposta = new LinkedHashMap<>();
                resposta.put("competencia", skill.nome());
                resposta.put("titulo", skill.titulo());
                resposta.put("roteiro", skill.conteudo());
                return ToolResult.of(formatter.json(resposta), Objects.toString(skill.titulo(), skill.nome()));
            }
        };
    }
}
