package com.nexo.app.modules.assistente.infrastructure.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.agent.*;
import com.nexo.app.modules.assistente.application.results.ResultFormatter;
import com.nexo.app.modules.assistente.application.results.ResultStore;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.application.semantic.ConsultaDadosService;
import com.nexo.app.modules.assistente.domain.ResultadoAssistente;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static com.nexo.app.modules.assistente.application.semantic.ViewQuery.*;

/**
 * Pacotes de indicadores prontos por dominio ("como esta o estoque hoje?"). Cada indicador e uma consulta
 * parametrizada sobre a camada semantica; indicadores de fontes sem permissao sao omitidos.
 */
@Component
public class IndicadoresTool implements AgentTool {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final List<String> DOMINIOS = List.of("estoque", "financeiro", "descontos", "sst", "chamados",
            "obras", "rh", "execucao");

    private final ConsultaDadosService consultas;
    private final ResultStore resultStore;
    private final ResultFormatter formatter;
    private final AssistenteProperties properties;

    public IndicadoresTool(ConsultaDadosService consultas, ResultStore resultStore, ResultFormatter formatter,
            AssistenteProperties properties) {
        this.consultas = consultas;
        this.resultStore = resultStore;
        this.formatter = formatter;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "obter_indicadores";
    }

    @Override
    public String displayName() {
        return "Obter indicadores";
    }

    @Override
    public String description() {
        return "Panorama pronto de indicadores de um dominio do Nexo: estoque (saldo, giro 30 dias, solicitacoes "
                + "pendentes, retiradas atrasadas, reservas por prazo, materiais com maior saldo), financeiro "
                + "(compras/pagamentos por status, aguardando aprovacao, vencimentos em 7 dias, documentos em aberto), "
                + "descontos, sst (inspecoes, desvios abertos, eventos, acoes vencidas), chamados (abertos, SLA), obras, "
                + "rh e execucao. Use como primeiro passo para perguntas gerais como 'como esta o estoque hoje?'.";
    }

    @Override
    public JsonNode inputSchema() {
        return JsonSchemas.object()
                .required("dominio", JsonSchemas.enumString("Dominio dos indicadores", DOMINIOS))
                .build();
    }

    @Override
    public ToolKind kind() {
        return ToolKind.DATA;
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(90);
    }

    @Override
    public boolean isAvailable(AgentRunContext context) {
        return consultas.disponivel();
    }

    @Override
    public String progressLabel(JsonNode arguments) {
        String dominio = ToolArgs.text(arguments, "dominio");
        return "Calculando indicadores" + (dominio == null ? "" : " de " + dominio);
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String dominio = ToolArgs.requiredText(arguments, "dominio").toLowerCase(Locale.ROOT);
        if (!DOMINIOS.contains(dominio)) throw new ToolException("Dominio invalido. Use: " + String.join(", ", DOMINIOS));
        LocalDate hoje = LocalDate.now(ZONA);
        List<Kpi> kpis = pacote(dominio, hoje);
        List<List<Object>> linhas = new ArrayList<>();
        List<String> omitidos = new ArrayList<>();
        for (Kpi kpi : kpis) {
            if (context.cancelled()) break;
            if (!consultas.podeConsultar(kpi.consulta().fonte(), context)) {
                omitidos.add(kpi.indicador());
                continue;
            }
            TabularData dados = consultas.executarSemRegistro(kpi.consulta(), context);
            int indiceValor = dados.columnIndex("valor");
            String dimensao = kpi.consulta().agruparPor().isEmpty() ? null : kpi.consulta().agruparPor().getFirst();
            int indiceDimensao = dimensao == null ? -1 : dados.columnIndex(dimensao);
            if (dados.rows().isEmpty() && dimensao == null) {
                linhas.add(linha(kpi, null, 0));
                continue;
            }
            for (List<Object> row : dados.rows()) {
                Object valor = indiceValor < 0 ? null : row.get(indiceValor);
                Object recorte = indiceDimensao < 0 ? null : row.get(indiceDimensao);
                linhas.add(linha(kpi, recorte == null ? null : String.valueOf(recorte), valor));
            }
        }
        if (linhas.isEmpty()) {
            throw new ToolException("Seu perfil nao tem acesso aos dados deste dominio.");
        }
        List<TabularData.Column> colunas = List.of(
                new TabularData.Column("indicador", TabularData.ColumnType.TEXTO),
                new TabularData.Column("recorte", TabularData.ColumnType.TEXTO),
                new TabularData.Column("valor", TabularData.ColumnType.NUMERO),
                new TabularData.Column("unidade", TabularData.ColumnType.TEXTO),
                new TabularData.Column("periodo", TabularData.ColumnType.TEXTO),
                new TabularData.Column("fonte", TabularData.ColumnType.TEXTO));
        ResultStore.StoredResult stored = resultStore.registrar(context.run(), name(), ResultadoAssistente.Natureza.INTERNO,
                "Indicadores de " + dominio + " em " + hoje, new TabularData(colunas, linhas, linhas.size(), false),
                Map.of("dominio", dominio, "data_referencia", hoje.toString()));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("data_referencia", hoje.toString());
        if (!omitidos.isEmpty()) extra.put("indicadores_sem_permissao", omitidos.size());
        return formatter.toToolResult(stored, properties.getAgent().getToolResultMaxChars(), extra, "Indicadores de " + dominio);
    }

    private static List<Object> linha(Kpi kpi, String recorte, Object valor) {
        List<Object> linha = new ArrayList<>();
        linha.add(kpi.indicador());
        linha.add(recorte);
        linha.add(valor == null ? 0 : valor);
        linha.add(kpi.unidade());
        linha.add(kpi.periodo());
        linha.add(kpi.consulta().fonte());
        return linha;
    }

    private record Kpi(String indicador, String unidade, String periodo, com.nexo.app.modules.assistente.application.semantic.ViewQuery consulta) {
    }

    private static com.nexo.app.modules.assistente.application.semantic.ViewQuery q(String fonte, String funcao,
            String coluna, List<Filtro> filtros, Periodo periodo, String dimensao, Integer limite, boolean desc) {
        return new com.nexo.app.modules.assistente.application.semantic.ViewQuery(fonte, List.of(),
                dimensao == null ? List.of() : List.of(dimensao), List.of(new Metrica(funcao, coluna, "valor")), filtros,
                periodo, desc ? List.of(new Ordem("valor", true)) : List.of(), limite);
    }

    private static Filtro f(String coluna, String operador, Object... valores) {
        return new Filtro(coluna, operador, List.of(valores));
    }

    private static List<Kpi> pacote(String dominio, LocalDate hoje) {
        LocalDate inicio30 = hoje.minusDays(29);
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        LocalDate inicio12m = hoje.minusMonths(12).plusDays(1);
        Periodo ultimos30 = new Periodo(null, inicio30, hoje);
        Periodo mes = new Periodo(null, inicioMes, hoje);
        Periodo doze = new Periodo(null, inicio12m, hoje);
        String p30 = inicio30 + " a " + hoje;
        String pMes = inicioMes + " a " + hoje;
        String p12 = inicio12m + " a " + hoje;
        return switch (dominio) {
            case "estoque" -> List.of(
                    new Kpi("Quantidade total em saldo", "unidades", "atual", q("saldos", "soma", "quantidade", List.of(), null, null, null, false)),
                    new Kpi("Saldo por condição", "unidades", "atual", q("saldos", "soma", "quantidade", List.of(), null, "condicao", 10, true)),
                    new Kpi("Materiais com saldo", "materiais", "atual", q("saldos", "contagem_distinta", "material_id", List.of(f("quantidade", "maior", 0)), null, null, null, false)),
                    new Kpi("Estoques com saldo", "estoques", "atual", q("saldos", "contagem_distinta", "estoque_id", List.of(f("quantidade", "maior", 0)), null, null, null, false)),
                    new Kpi("Materiais com maior saldo", "unidades", "atual", q("saldos", "soma", "quantidade", List.of(), null, "material", 5, true)),
                    new Kpi("Movimentações nos últimos 30 dias", "movimentações", p30, q("movimentacoes", "contagem", null, List.of(), ultimos30, null, null, false)),
                    new Kpi("Movimentações por tipo (30 dias)", "movimentações", p30, q("movimentacoes", "contagem", null, List.of(), ultimos30, "tipo_movimentacao", 10, true)),
                    new Kpi("Solicitações pendentes de aprovação", "solicitações", "atual", q("solicitacoes", "contagem", null, List.of(f("status", "igual", "PENDENTE")), null, null, null, false)),
                    new Kpi("Solicitações aprovadas aguardando entrega", "solicitações", "atual", q("solicitacoes", "contagem", null, List.of(f("status", "igual", "APROVADO")), null, null, null, false)),
                    new Kpi("Retiradas atrasadas", "solicitações", "atual", q("solicitacoes", "contagem", null, List.of(f("retirada_atrasada", "igual", true)), null, null, null, false)),
                    new Kpi("Reservas por situação de prazo", "reservas", "atual", q("reservas", "contagem", null, List.of(f("situacao_prazo", "diferente", "CONCLUIDA")), null, "situacao_prazo", 10, true)));
            case "financeiro" -> List.of(
                    new Kpi("Compras por status (mês)", "solicitações", pMes, q("solicitacoes_compra", "contagem", null, List.of(), mes, "status", 10, true)),
                    new Kpi("Valor de compras por status (mês)", "R$", pMes, q("solicitacoes_compra", "soma", "valor", List.of(), mes, "status", 10, true)),
                    new Kpi("Pagamentos por status (mês)", "solicitações", pMes, q("solicitacoes_pagamento", "contagem", null, List.of(), mes, "status", 10, true)),
                    new Kpi("Valor de pagamentos por status (mês)", "R$", pMes, q("solicitacoes_pagamento", "soma", "valor", List.of(), mes, "status", 10, true)),
                    new Kpi("Compras aguardando aprovação", "solicitações", "atual", q("solicitacoes_compra", "contagem", null, List.of(f("status", "igual", "SOLICITADO")), null, null, null, false)),
                    new Kpi("Valor de compras aguardando aprovação", "R$", "atual", q("solicitacoes_compra", "soma", "valor", List.of(f("status", "igual", "SOLICITADO")), null, null, null, false)),
                    new Kpi("Pagamentos aguardando aprovação", "solicitações", "atual", q("solicitacoes_pagamento", "contagem", null, List.of(f("status", "igual", "SOLICITADO")), null, null, null, false)),
                    new Kpi("Pagamentos vencendo em até 7 dias", "solicitações", "proximos 7 dias", q("solicitacoes_pagamento", "contagem", null, List.of(f("dias_para_vencimento", "entre", 0, 7), f("status", "em", "SOLICITADO", "APROVADO")), null, null, null, false)),
                    new Kpi("Documentos financeiros em aberto", "documentos", "atual", q("documentos_financeiros", "contagem", null, List.of(f("em_aberto", "igual", true)), null, "tipo", 10, true)),
                    new Kpi("Despesas aprovadas por tipo (12 meses)", "R$", p12, q("solicitacoes_compra", "soma", "valor_aprovado", List.of(f("status", "igual", "APROVADO")), doze, "tipo_despesa", 10, true)));
            case "descontos" -> List.of(
                    new Kpi("Descontos por status (12 meses)", "descontos", p12, q("descontos", "contagem", null, List.of(), doze, "status", 10, true)),
                    new Kpi("Valor por status (12 meses)", "R$", p12, q("descontos", "soma", "valor_referencia", List.of(), doze, "status", 10, true)),
                    new Kpi("Valor baixado por motivo (12 meses)", "R$", p12, q("descontos", "soma", "valor_baixado", List.of(f("status", "igual", "BAIXADO")), doze, "motivo", 10, true)),
                    new Kpi("Descontos aguardando validação", "descontos", "atual", q("descontos", "contagem", null, List.of(f("status", "igual", "SOLICITADO")), null, null, null, false)),
                    new Kpi("Tempo médio até validação", "dias", p12, q("descontos", "media", "dias_ate_validacao", List.of(), doze, null, null, false)));
            case "sst" -> List.of(
                    new Kpi("Inspeções no mês", "inspeções", pMes, q("inspecoes", "contagem", null, List.of(f("status", "diferente", "CANCELADA")), mes, null, null, false)),
                    new Kpi("Desvios abertos por gravidade", "desvios", "atual", q("desvios", "contagem", null, List.of(f("status", "diferente", "RESOLVIDO")), null, "gravidade", 10, true)),
                    new Kpi("Desvios com prazo vencido", "desvios", "atual", q("desvios", "contagem", null, List.of(f("prazo_vencido", "igual", true)), null, null, null, false)),
                    new Kpi("Eventos por tipo (12 meses)", "eventos", p12, q("eventos_sst", "contagem", null, List.of(f("status", "diferente", "CANCELADO")), doze, "tipo", 10, true)),
                    new Kpi("Dias perdidos (12 meses)", "dias", p12, q("eventos_sst", "soma", "dias_perdidos", List.of(f("status", "diferente", "CANCELADO")), doze, null, null, false)),
                    new Kpi("Ações de governança vencidas", "ações", "atual", q("acoes_governanca", "contagem", null, List.of(f("situacao_prazo", "igual", "VENCIDA")), null, null, null, false)));
            case "chamados" -> List.of(
                    new Kpi("Chamados em aberto por status", "chamados", "atual", q("chamados", "contagem", null, List.of(f("status", "em", "ABERTO", "EM_ATENDIMENTO", "AGUARDANDO_SOLICITANTE")), null, "status", 10, true)),
                    new Kpi("Chamados abertos por prioridade", "chamados", "atual", q("chamados", "contagem", null, List.of(f("status", "em", "ABERTO", "EM_ATENDIMENTO", "AGUARDANDO_SOLICITANTE")), null, "prioridade", 10, true)),
                    new Kpi("Chamados em aberto com SLA de resolução vencido", "chamados", "atual", q("chamados", "contagem", null, List.of(f("sla_resolucao_vencido", "igual", true), f("status", "em", "ABERTO", "EM_ATENDIMENTO", "AGUARDANDO_SOLICITANTE")), null, null, null, false)),
                    new Kpi("Chamados abertos no mês", "chamados", pMes, q("chamados", "contagem", null, List.of(), mes, null, null, false)),
                    new Kpi("Tempo médio de resolução (12 meses)", "horas", p12, q("chamados", "media", "horas_ate_resolucao", List.of(), doze, null, null, false)));
            case "obras" -> List.of(
                    new Kpi("Obras por status", "obras", "atual", q("obras", "contagem", null, List.of(f("ativo", "igual", true)), null, "status", 20, true)),
                    new Kpi("Obras atrasadas", "obras", "atual", q("obras", "contagem", null, List.of(f("atrasada", "igual", true)), null, null, null, false)),
                    new Kpi("Valor total dos projetos ativos", "R$", "atual", q("obras", "soma", "valor_projeto", List.of(f("ativo", "igual", true)), null, null, null, false)),
                    new Kpi("Obras por tipo", "obras", "atual", q("obras", "contagem", null, List.of(f("ativo", "igual", true)), null, "tipo_obra", 10, true)));
            case "rh" -> List.of(
                    new Kpi("Colaboradores ativos por base", "colaboradores", "atual", q("colaboradores", "contagem", null, List.of(f("ativo", "igual", true)), null, "base_operacional", 20, true)),
                    new Kpi("Colaboradores ativos por função", "colaboradores", "atual", q("colaboradores", "contagem", null, List.of(f("ativo", "igual", true)), null, "funcao", 20, true)),
                    new Kpi("Equipes ativas", "equipes", "atual", q("equipes", "contagem", null, List.of(f("ativo", "igual", true)), null, null, null, false)));
            case "execucao" -> List.of(
                    new Kpi("Execuções comerciais (30 dias)", "execuções", p30, q("execucoes_comerciais", "contagem", null, List.of(), ultimos30, "status_execucao", 10, true)),
                    new Kpi("Valor faturado (30 dias)", "R$", p30, q("execucoes_comerciais", "soma", "valor_total_faturado", List.of(), ultimos30, null, null, false)),
                    new Kpi("Turnos abertos", "turnos", "atual", q("turnos", "contagem", null, List.of(f("status", "igual", "ABERTO")), null, null, null, false)));
            default -> List.of();
        };
    }
}
