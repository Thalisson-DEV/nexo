package com.nexo.app.modules.automacao.application.usecases;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.nexo.app.modules.automacao.domain.TipoAutomacao;

/**
 * Modelos prontos para começar: os casos que motivaram o motor, já montados. A pessoa
 * escolhe um, completa quem recebe e ativa.
 */
@Component
public class ModelosAutomacao {

    public record Modelo(String id, String nome, String descricao, TipoAutomacao tipo, String icone, Map<String, Object> definicao) {
    }

    public List<Modelo> listar() {
        return List.of(descontoRh(), relatorioComercialPorBase(), relatorioSemanal(), filaDescontos(), aprovacaoAntesDeEnviar());
    }

    private static Map<String, Object> no(String id, String tipo, String rotulo, Map<String, Object> params) {
        Map<String, Object> no = new LinkedHashMap<>();
        no.put("id", id);
        no.put("type", tipo);
        no.put("label", rotulo);
        no.put("params", new LinkedHashMap<>(params));
        return no;
    }

    private static Map<String, Object> definicao(String gatilho, Map<String, Object> params, List<Object> passos) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("schema", 3);
        d.put("trigger", new LinkedHashMap<>(Map.of("type", gatilho, "params", new LinkedHashMap<>(params))));
        d.put("variables", new ArrayList<>());
        d.put("steps", passos);
        d.put("settings", new LinkedHashMap<>(Map.of("notify_on_failure", new ArrayList<>(), "timeout_hours", 168)));
        d.put("layout", new LinkedHashMap<>());
        return d;
    }

    private Modelo descontoRh() {
        Map<String, Object> rotear = no("rh_da_base", "people.route", "RH da base do colaborador", Map.of(
                "value", "{{ trigger.outputs.colaborador.base }}",
                "routes", List.of(
                        Map.of("when", "Juazeiro", "recipients", List.of()),
                        Map.of("when", "Petrolina", "recipients", List.of())),
                "fallback", List.of(Map.of("tipo", "permissao", "valor", "desconto:baixar", "rotulo", "Quem dá baixa em descontos")),
                "match", "first", "compare", "eq"));
        Map<String, Object> avisar = no("avisar_rh", "notify.people", "Avisar o RH", Map.of(
                "recipients", List.of(Map.of("tipo", "expressao", "valor", "{{ steps.rh_da_base.outputs.recipients }}")),
                "title", "Desconto validado aguardando baixa",
                "body", "O desconto de {{ trigger.outputs.colaborador.nome }} ({{ trigger.outputs.colaborador.base }}) foi validado "
                        + "em {{ format_money(trigger.outputs.valorValidado) }} e aguarda a baixa.",
                "link", "/dp/descontos/{{ trigger.outputs.idDesconto }}",
                "email", true, "category", "APROVACAO", "priority", "ALTA"));
        return new Modelo("desconto-rh-base", "Desconto validado → RH da base do colaborador",
                "Quando um desconto é validado, avisa (sininho + e-mail) o RH da base do colaborador; se a base não estiver na tabela, avisa todo o RH.",
                TipoAutomacao.ALERTA, "route", definicao("event.desconto.validado", Map.of(), new ArrayList<>(List.of(rotear, avisar))));
    }

    public static final String RELATORIO_COMERCIAL_ID = "relatorio-comercial-bases";
    public static final String RELATORIO_COMERCIAL_NOME = "Relatório semanal do comercial por base (Copiloto)";

    /**
     * Toda segunda, uma análise por base (Juazeiro, Remanso, Jacobina e Bonfim): execuções de
     * campo, faturamento e metas, materiais aplicados e solicitações de material do comercial.
     * Cada base recebe só o seu relatório; quem recebe fica na tabela "Quem recebe cada base".
     * Base sem ninguém na tabela é pulada (sem gastar a análise).
     */
    public Modelo relatorioComercialPorBase() {
        Map<String, Object> destinatarios = no("quem_recebe", "people.route", "Quem recebe cada base", Map.of(
                "value", "{{ item }}",
                "routes", List.of(
                        Map.of("when", "JUAZEIRO", "recipients", List.of()),
                        Map.of("when", "REMANSO", "recipients", List.of()),
                        Map.of("when", "JACOBINA", "recipients", List.of()),
                        Map.of("when", "BONFIM", "recipients", List.of())),
                "fallback", List.of(), "match", "first", "compare", "eq"));

        String anteriorInicio = "{{ format_date(add_days(trigger.outputs.semana_passada_inicio, -7), 'dd/mm/aaaa') }}";
        String anteriorFim = "{{ format_date(add_days(trigger.outputs.semana_passada_inicio, -1), 'dd/mm/aaaa') }}";
        List<Object> passos = new ArrayList<>();
        passos.add(consulta("q_equipes", "Execuções por equipe e tipo de serviço", "execucoes_comerciais", "base_operacional",
                List.of("equipe", "tipo_servico"), List.of(metrica("contagem", "execucao_id", "notas"),
                        metrica("soma", "valor_total_faturado", "faturado")), "last_week", null, null, "faturado", 200, List.of()));
        passos.add(consulta("q_equipes_anterior", "Execuções por equipe na semana anterior", "execucoes_comerciais", "base_operacional",
                List.of("equipe"), List.of(metrica("contagem", "execucao_id", "notas"),
                        metrica("soma", "valor_total_faturado", "faturado")), "custom", anteriorInicio, anteriorFim, "faturado", 200, List.of()));
        passos.add(consulta("q_turnos", "Turnos das equipes", "turnos", "base_operacional",
                List.of("equipe", "encerrado_automaticamente"), List.of(metrica("contagem", "turno_id", "turnos"),
                        metrica("soma", "duracao_horas", "horas")), "last_week", null, null, "horas", 200, List.of()));
        passos.add(consulta("q_materiais", "Materiais aplicados e retirados", "execucoes_materiais", "base_operacional",
                List.of("material", "unidade_medida", "operacao"), List.of(metrica("soma", "quantidade", "quantidade"),
                        metrica("contagem_distinta", "execucao_id", "execucoes")), "last_week", null, null, "quantidade", 30, List.of()));
        passos.add(consulta("q_solicitacoes", "Solicitações de material do comercial", "solicitacoes", "base_destino",
                List.of("status", "retirada_atrasada", "fora_prazo_saque"), List.of(metrica("contagem", "solicitacao_id", "solicitacoes"),
                        metrica("media", "horas_ate_decisao", "horas_ate_decisao"), metrica("media", "horas_ate_entrega", "horas_ate_entrega"),
                        metrica("soma", "quantidade_solicitada", "qtd_solicitada"), metrica("soma", "quantidade_entregue", "qtd_entregue")),
                "last_week", null, null, "solicitacoes", 100, List.of(Map.of("coluna", "contexto", "operador", "igual", "valor", "COMERCIAL"))));
        Map<String, Object> metas = consulta("q_metas", "Metas de faturamento vigentes", "metas_faturamento", "base_operacional",
                List.of(), List.of(), "none", null, null, null, 200, List.of(Map.of("coluna", "vigente", "operador", "igual", "valor", "true")));
        @SuppressWarnings("unchecked")
        Map<String, Object> paramsMetas = (Map<String, Object>) metas.get("params");
        paramsMetas.put("columns", new ArrayList<>(List.of("equipe", "processo", "periodicidade", "meta_valor", "vigencia_inicio", "vigencia_fim")));
        passos.add(metas);

        String dados = "Base: {{ item }}\n"
                + "Semana analisada: {{ trigger.outputs.semana_passada_inicio }} a {{ trigger.outputs.semana_passada_fim }}\n"
                + "Semana anterior: " + anteriorInicio + " a " + anteriorFim + "\n\n"
                + "EXECUCOES POR EQUIPE E TIPO DE SERVICO (semana analisada): {{ to_json(steps.q_equipes.outputs.rows) }}\n\n"
                + "EXECUCOES POR EQUIPE (semana anterior): {{ to_json(steps.q_equipes_anterior.outputs.rows) }}\n\n"
                + "TURNOS POR EQUIPE (semana analisada): {{ to_json(steps.q_turnos.outputs.rows) }}\n\n"
                + "MATERIAIS DAS EXECUCOES (semana analisada, 30 maiores): {{ to_json(steps.q_materiais.outputs.rows) }}\n\n"
                + "SOLICITACOES DE MATERIAL DO COMERCIAL (semana analisada, por status): {{ to_json(steps.q_solicitacoes.outputs.rows) }}\n\n"
                + "METAS DE FATURAMENTO VIGENTES: {{ to_json(steps.q_metas.outputs.rows) }}";
        Map<String, Object> analise = no("analise", "ai.copiloto", "Copiloto analisa o comercial da base", Map.of(
                "prompt", "Escreva a análise prévia do processo comercial da base {{ item }} na semana de "
                        + "{{ trigger.outputs.semana_passada_inicio }} a {{ trigger.outputs.semana_passada_fim }}, para a coordenação da base.\n"
                        + "Os números da semana já foram consultados e estão nos dados extras (execuções, turnos, materiais, solicitações e metas). "
                        + "Use esses números como fonte principal e não refaça essas consultas. Consulte o Nexo só para o que faltar: "
                        + "o faturamento do ciclo comercial atual por equipe (de 26 do mês anterior até hoje, em execucoes_comerciais, "
                        + "coluna data_lancamento) para calcular o atingimento das metas, e as equipes ativas da base sem lançamento na semana.\n"
                        + "Estrutura: 1) Resumo executivo de até 5 linhas; 2) Execuções de campo: notas e faturamento por equipe e por tipo de serviço, "
                        + "com a variação contra a semana anterior; 3) Turnos: horas trabalhadas e turnos encerrados automaticamente; "
                        + "4) Metas: atingimento do ciclo por equipe, quem está abaixo e quem mais evoluiu; "
                        + "5) Materiais: os 10 principais e consumos fora do padrão; 6) Solicitações de material do comercial: por status, "
                        + "tempo médio até a decisão e até a entrega, retiradas atrasadas e pedidos fora do prazo de saque; "
                        + "7) Pontos de atenção e recomendações práticas.\n"
                        + "Use tabelas curtas. Quando um dado vier vazio, diga que não houve registro em vez de estimar. "
                        + "Gere o relatório em PDF com o mesmo conteúdo.",
                "context", dados,
                "pdf", true, "title", "Comercial {{ item }} — semana de {{ trigger.outputs.semana_passada_inicio }}", "timeout_minutes", 15));
        analise.put("run_after", new ArrayList<>(List.of("succeeded", "failed")));
        passos.add(analise);
        String corpo = "<p>Bom dia! Segue a análise prévia do comercial da base <strong><span data-expr=\"item\"></span></strong> "
                + "na semana de <span data-expr=\"trigger.outputs.semana_passada_inicio\"></span> a "
                + "<span data-expr=\"trigger.outputs.semana_passada_fim\"></span>: execuções de campo, metas, materiais e solicitações.</p>"
                + "<div data-block=\"markdown\" data-source=\"steps.analise.outputs.text\"></div>"
                + "<p>O relatório completo está em anexo (PDF). Para aprofundar algum ponto, continue a conversa com o Copiloto.</p>";
        passos.add(no("enviar", "email.send", "Enviar o relatório da base", Map.of(
                "to", List.of(Map.of("tipo", "expressao", "valor", "{{ steps.quem_recebe.outputs.recipients }}", "rotulo", "Quem recebe esta base")),
                "subject", "Comercial {{ item }} — semana de {{ trigger.outputs.semana_passada_inicio }} a {{ trigger.outputs.semana_passada_fim }}",
                "body", corpo,
                "attachments", "{{ steps.analise.outputs.files }}",
                "label", "Comercial · {{ item }}", "button_label", "Continuar a conversa com o Copiloto",
                "button_link", "{{ steps.analise.outputs.conversation_link }}", "tone", "INFORMATIVO")));

        Map<String, Object> temQuem = no("tem_quem_receba", "control.condition", "A base tem quem receba?", Map.of(
                "condition", Map.of("op", "and", "rules", List.of(
                        Map.of("left", "{{ steps.quem_recebe.outputs.recipients }}", "operator", "not_empty", "right", "")))));
        temQuem.put("then", passos);
        temQuem.put("else", new ArrayList<>());
        Map<String, Object> porBase = no("cada_base", "control.foreach", "Para cada base", Map.of("items", "{{ split(vars.bases, ';') }}"));
        porBase.put("steps", new ArrayList<>(List.of(destinatarios, temQuem)));
        Map<String, Object> def = definicao("trigger.schedule",
                Map.of("frequency", "week", "weekdays", List.of(0), "time", "07:00"), new ArrayList<>(List.of(porBase)));
        def.put("variables", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of(
                "name", "bases", "type", "string", "value", "JUAZEIRO;REMANSO;JACOBINA;BONFIM",
                "description", "Bases que recebem o relatório, separadas por ponto e vírgula")))));
        return new Modelo(RELATORIO_COMERCIAL_ID, RELATORIO_COMERCIAL_NOME,
                "Toda segunda às 7h, uma análise prévia do comercial de cada base (execuções de campo, metas, materiais e solicitações), "
                        + "com PDF, enviada só para quem você indicar em cada base.",
                TipoAutomacao.DADOS_IA, "sparkles", def);
    }

    /** Passo que só a versão com consultas prontas tem: marca se uma automação já foi atualizada. */
    public static final String RELATORIO_COMERCIAL_MARCA = "q_equipes";

    private static Map<String, Object> metrica(String funcao, String coluna, String alias) {
        return Map.of("funcao", funcao, "coluna", coluna, "alias", alias);
    }

    /**
     * Consulta agregada de uma fonte, filtrada pela base do item atual. Roda mesmo que a
     * consulta anterior falhe: um dado faltando não derruba o relatório.
     */
    private static Map<String, Object> consulta(String id, String rotulo, String fonte, String colunaBase, List<String> agrupar,
                                                List<Object> metricas, String periodo, String inicio, String fim, String ordenar,
                                                int limite, List<Object> filtrosExtras) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("source", fonte);
        params.put("columns", new ArrayList<>());
        params.put("group_by", new ArrayList<>(agrupar));
        params.put("metrics", new ArrayList<>(metricas));
        List<Object> filtros = new ArrayList<>();
        filtros.add(Map.of("coluna", colunaBase, "operador", "igual", "valor", "{{ item }}"));
        filtros.addAll(filtrosExtras);
        params.put("filters", filtros);
        params.put("period", periodo);
        if (inicio != null) params.put("start", inicio);
        if (fim != null) params.put("end", fim);
        if (ordenar != null) params.put("order_by", ordenar);
        params.put("desc", true);
        params.put("limit", limite);
        Map<String, Object> no = no(id, "ces.query", rotulo, params);
        no.put("run_after", new ArrayList<>(List.of("succeeded", "failed")));
        return no;
    }

    private Modelo relatorioSemanal() {
        Map<String, Object> analise = no("analise", "ai.copiloto", "Copiloto analisa a produtividade", Map.of(
                "prompt", "Analise a produtividade das equipes das bases Juazeiro e Petrolina na semana passada "
                        + "({{ trigger.outputs.semana_passada_inicio }} a {{ trigger.outputs.semana_passada_fim }}): serviços executados, "
                        + "faturamento e cumprimento de meta por equipe, comparando com a semana anterior. Destaque as equipes abaixo da meta "
                        + "e as que mais evoluíram, com recomendações práticas para a coordenação.",
                "pdf", true, "title", "Relatório semanal de produtividade", "timeout_minutes", 10));
        String corpo = "<p>Bom dia! Segue a análise semanal de produtividade das equipes "
                + "(<span data-expr=\"trigger.outputs.semana_passada_inicio\"></span> a <span data-expr=\"trigger.outputs.semana_passada_fim\"></span>).</p>"
                + "<div data-block=\"markdown\" data-source=\"steps.analise.outputs.text\"></div>"
                + "<p>O relatório completo, com gráficos, está em anexo.</p>";
        Map<String, Object> email = no("enviar", "email.send", "Enviar o relatório", Map.of(
                "to", List.of(),
                "subject", "Produtividade semanal das equipes — {{ trigger.outputs.semana_passada_inicio }} a {{ trigger.outputs.semana_passada_fim }}",
                "body", corpo,
                "attachments", "{{ steps.analise.outputs.files }}",
                "label", "Relatório semanal", "button_label", "Continuar a conversa com o Copiloto",
                "button_link", "{{ steps.analise.outputs.conversation_link }}", "tone", "INFORMATIVO"));
        return new Modelo("relatorio-semanal-copiloto", "Relatório semanal de produtividade (Copiloto)",
                "Toda segunda às 7h o Copiloto analisa a produtividade das equipes das bases escolhidas e envia o relatório por e-mail, com PDF.",
                TipoAutomacao.ROTINA, "sparkles", definicao("trigger.schedule",
                Map.of("frequency", "week", "weekdays", List.of(0), "time", "07:00"), new ArrayList<>(List.of(analise, email))));
    }

    private Modelo filaDescontos() {
        Map<String, Object> avisar = no("avisar", "notify.people", "Avisar quem aprova", Map.of(
                "recipients", List.of(Map.of("tipo", "permissao", "valor", "desconto:validar", "rotulo", "Quem valida descontos")),
                "title", "Descontos aguardando sua aprovação",
                "body", "Há {{ trigger.outputs.total }} desconto(s) solicitado(s) aguardando análise.",
                "link", "/dp/descontos/lancamento", "email", true, "category", "APROVACAO", "priority", "NORMAL"));
        return new Modelo("fila-descontos", "Fila de descontos acumulando",
                "Quando a fila de descontos pendentes passa do limite, avisa quem aprova (com silêncio entre avisos).",
                TipoAutomacao.ALERTA, "gauge", definicao("threshold.desconto.pendente_aprovacao",
                Map.of("limiar", 10, "silencio_minutos", 240, "verificar_minutos", 15), new ArrayList<>(List.of(avisar))));
    }

    private Modelo aprovacaoAntesDeEnviar() {
        Map<String, Object> resumo = no("resumo", "ai.copiloto", "Copiloto prepara o resumo", Map.of(
                "prompt", "Resuma os chamados abertos há mais de 5 dias, por equipe responsável, com os mais antigos primeiro.",
                "pdf", false, "timeout_minutes", 8));
        Map<String, Object> aprovar = no("aprovar", "approval.request", "Coordenador confere", Map.of(
                "title", "Enviar o resumo de chamados para a diretoria?",
                "details", "{{ steps.resumo.outputs.text }}",
                "approvers", List.of(), "kind", "approve_reject", "expires_after_hours", 24));
        Map<String, Object> enviar = no("enviar", "email.send", "Enviar à diretoria", Map.of(
                "to", List.of(), "subject", "Chamados parados há mais de 5 dias",
                "body", "<div data-block=\"markdown\" data-source=\"steps.resumo.outputs.text\"></div>"));
        Map<String, Object> condicao = no("aprovado", "control.condition", "Foi aprovado?", Map.of(
                "condition", Map.of("op", "and", "rules", List.of(Map.of("left", "{{ steps.aprovar.outputs.approved }}", "operator", "is_true", "right", "")))));
        condicao.put("then", new ArrayList<>(List.of(enviar)));
        condicao.put("else", new ArrayList<>());
        return new Modelo("aprovacao-antes-envio", "Resumo com aprovação antes de enviar",
                "Toda sexta o Copiloto resume os chamados parados; um coordenador aprova no sininho e só então o e-mail sai.",
                TipoAutomacao.APROVACAO, "check", definicao("trigger.schedule",
                Map.of("frequency", "week", "weekdays", List.of(4), "time", "16:00"), new ArrayList<>(List.of(resumo, aprovar, condicao))));
    }
}
