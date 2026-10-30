package com.nexo.app.modules.automacao.infrastructure.copiloto;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.app.modules.assistente.application.agent.BaseTool;
import com.nexo.app.modules.assistente.application.agent.JsonSchemas;
import com.nexo.app.modules.assistente.application.agent.ToolArgs;
import com.nexo.app.modules.assistente.application.agent.ToolContext;
import com.nexo.app.modules.assistente.application.agent.ToolException;
import com.nexo.app.modules.assistente.application.agent.ToolKind;
import com.nexo.app.modules.assistente.application.agent.ToolResult;
import com.nexo.app.modules.assistente.application.dto.AcaoInterface;
import com.nexo.app.modules.automacao.application.dto.RascunhoResponse;
import com.nexo.app.modules.automacao.application.usecases.RascunhoAutomacaoService;
import com.nexo.app.modules.automacao.domain.expressao.Valores;
import com.nexo.app.shared.domain.exception.BaseBusinessException;

/**
 * Ferramenta do chat: "Copiloto, toda segunda me mande…" vira um rascunho de
 * automação, com o botão "Abrir no editor". Nada é ativado pelo chat — quem ativa é a
 * pessoa, no editor, depois de conferir.
 */
@Component
public class FerramentaRascunhoAutomacao extends BaseTool {

    public FerramentaRascunhoAutomacao(ObjectProvider<RascunhoAutomacaoService> rascunhos) {
        super("criar_rascunho_automacao", "Montar automação",
                "Cria (ou ajusta) uma AUTOMACAO do Nexo a partir do pedido do usuario: avisos e e-mails quando algo acontece "
                        + "(ex.: desconto validado -> avisar o RH da base do colaborador), relatorios agendados com analise do Copiloto, "
                        + "aprovacoes, rotinas. Use quando o usuario pedir para automatizar, agendar, avisar sempre que, mandar toda semana. "
                        + "A automacao fica salva como RASCUNHO; diga o que foi montado, o que falta informar e que ele pode abrir no editor "
                        + "para conferir e ativar. Para ajustar uma automacao existente, informe automacao_id.",
                JsonSchemas.object()
                        .required("pedido", JsonSchemas.string("O pedido completo do usuario, com todos os detalhes (quem recebe, quando, bases, horarios)"))
                        .prop("automacao_id", JsonSchemas.string("Id (UUID) da automacao a ajustar, quando o usuario pede mudanca numa existente"))
                        .build(),
                ToolKind.WRITE, Set.of("automacao:manage"), Duration.ofSeconds(170));
        this.rascunhos = rascunhos;
    }

    /** Preguiçoso: o rascunho usa o Copiloto, que usa o registro de ferramentas que contém esta. */
    private final ObjectProvider<RascunhoAutomacaoService> rascunhos;

    @Override
    public String progressLabel(JsonNode arguments) {
        return "Montando a automação";
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String pedido = ToolArgs.requiredText(arguments, "pedido");
        String id = ToolArgs.text(arguments, "automacao_id");
        UUID automacao = null;
        if (id != null && !id.isBlank()) {
            try {
                automacao = UUID.fromString(id.strip());
            } catch (IllegalArgumentException e) {
                throw new ToolException("automacao_id invalido");
            }
        }
        RascunhoResponse r;
        try {
            r = rascunhos.getObject().rascunhar(pedido, automacao, context.usuarioId());
        } catch (BaseBusinessException e) {
            throw new ToolException(e.getMessage());
        }
        context.run().navegacao().record(new AcaoInterface(AcaoInterface.NAVIGATE, "AUTOMACAO_EDITOR", "Abrir no editor",
                "automacao:manage", Map.of("id", r.automacaoId().toString())));
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("automacao_id", r.automacaoId().toString());
        resposta.put("nome", r.nome());
        resposta.put("tipo", r.tipo().getRotulo());
        resposta.put("resumo", r.resumo());
        resposta.put("passos", Valores.lista(r.definicao().get("steps")).stream()
                .map(p -> Valores.texto(Valores.mapa(p).get("label"))).toList());
        resposta.put("faltando", r.faltando());
        resposta.put("pendencias_para_ativar", r.problemas());
        resposta.put("orientacao", "Explique em poucas linhas o que a automacao faz, liste o que falta informar e avise que ela "
                + "ficou como rascunho: o botao 'Abrir no editor' ja esta disponivel. Nao escreva links.");
        return ToolResult.of(Valores.paraJson(resposta), "Rascunho: " + r.nome());
    }
}
