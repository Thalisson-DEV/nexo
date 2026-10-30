package com.nexo.app.modules.automacao.application.nos;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecCampo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecNo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecParam;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecSaida;
import com.nexo.app.modules.automacao.application.motor.Catalogo.ProvedorNos;
import com.nexo.app.modules.automacao.application.motor.Recorrencia;
import com.nexo.app.modules.automacao.application.usecases.EnriquecedorEntidades;
import com.nexo.app.modules.automacao.application.usecases.Pessoa;
import com.nexo.app.modules.automacao.domain.expressao.Condicoes;
import com.nexo.app.modules.automacao.domain.expressao.Valores;
import com.nexo.app.modules.notificacao.GatilhoCatalogado;
import com.nexo.app.modules.notificacao.NotificacaoApi;

/**
 * Gatilhos: o que começa uma execução e a saída que ele congela para os passos.
 *
 * <p>Os gatilhos de evento são gerados do catálogo de gatilhos do Nexo — um por fato
 * ("Desconto validado", "Chamado aberto"…). Além das variáveis do evento, a saída traz as
 * <b>entidades por trás dos ids</b>: o colaborador do desconto com base, função e
 * usuário; cada papel do evento (solicitante, responsável…) como pessoa. É isso que
 * permite rotear pelo RH da base do colaborador sem que o módulo de descontos saiba que
 * existe roteamento.</p>
 */
@Component
public class NosGatilhos implements ProvedorNos {

    private static final Map<String, String> ROTULOS = new LinkedHashMap<>();
    private static final Map<String, String> SUBGRUPOS = Map.ofEntries(
            Map.entry("desconto", "Descontos"), Map.entry("chamado", "Chamados"), Map.entry("financeiro", "Financeiro"),
            Map.entry("estoque", "Almoxarifado"), Map.entry("sst", "Segurança do trabalho"), Map.entry("frota", "Frota"),
            Map.entry("mencao", "Marcações"), Map.entry("observability", "Sistema"));

    static {
        ROTULOS.put("motivo", "Motivo");
        ROTULOS.put("parcelas", "Parcelas");
        ROTULOS.put("dataOcorrencia", "Data da ocorrência");
        ROTULOS.put("idDesconto", "Id do desconto");
        ROTULOS.put("idColaborador", "Id do colaborador");
        ROTULOS.put("valorValidado", "Valor validado");
        ROTULOS.put("valorBaixado", "Valor baixado");
        ROTULOS.put("motivoRejeicao", "Motivo da recusa");
        ROTULOS.put("protocolo", "Protocolo");
        ROTULOS.put("assunto", "Assunto");
        ROTULOS.put("topico", "Tópico");
        ROTULOS.put("prioridade", "Prioridade");
        ROTULOS.put("equipe", "Equipe");
        ROTULOS.put("idChamado", "Id do chamado");
        ROTULOS.put("autor", "Autor");
        ROTULOS.put("trecho", "Trecho");
        ROTULOS.put("solucao", "Solução");
        ROTULOS.put("prazoReaberturaHoras", "Prazo para reabrir (horas)");
        ROTULOS.put("tipo", "Tipo");
        ROTULOS.put("numero", "Número");
        ROTULOS.put("valor", "Valor");
        ROTULOS.put("valorAprovado", "Valor aprovado");
        ROTULOS.put("descricao", "Descrição");
        ROTULOS.put("observacao", "Observação");
        ROTULOS.put("idSolicitacao", "Id da solicitação");
        ROTULOS.put("base", "Base");
        ROTULOS.put("uf", "UF");
        ROTULOS.put("frota", "Frota");
        ROTULOS.put("obraCivil", "Obra civil");
        ROTULOS.put("estoqueOrigem", "Estoque de origem");
        ROTULOS.put("estoqueDestino", "Estoque de destino");
        ROTULOS.put("totalItens", "Total de itens");
        ROTULOS.put("justificativa", "Justificativa");
        ROTULOS.put("origem", "Origem");
        ROTULOS.put("tipoOrigem", "Tipo de origem");
        ROTULOS.put("link", "Link");
        ROTULOS.put("numeroRegistro", "Número do registro");
        ROTULOS.put("tipoRotulo", "Tipo (descrição)");
        ROTULOS.put("atividade", "Atividade");
        ROTULOS.put("comLesao", "Com lesão");
        ROTULOS.put("comunicacaoAtrasada", "Comunicação atrasada");
        ROTULOS.put("idEvento", "Id do evento");
        ROTULOS.put("tratativa", "Tratativa");
        ROTULOS.put("tipoAlvo", "Tipo do alvo");
        ROTULOS.put("referencia", "Referência");
        ROTULOS.put("dataVencimento", "Data de vencimento");
        ROTULOS.put("diasRestantes", "Dias restantes");
        ROTULOS.put("prazo", "Prazo");
        ROTULOS.put("ambiente", "Ambiente");
        ROTULOS.put("componente", "Componente");
        ROTULOS.put("estadoInicial", "Estado inicial");
        ROTULOS.put("resumo", "Resumo");
        ROTULOS.put("linkStatus", "Link do status");
        ROTULOS.put("runbook", "Procedimento");
        ROTULOS.put("total", "Total");
        ROTULOS.put("limiar", "Limiar");
        ROTULOS.put("solicitante", "Solicitante");
        ROTULOS.put("responsavel", "Responsável");
        ROTULOS.put("requisitante", "Requisitante");
        ROTULOS.put("mencionado", "Pessoa marcada");
        ROTULOS.put("equipeAtendimento", "Equipe de atendimento");
    }

    static final List<EspecSaida> SAIDAS_DATA = List.of(
            EspecSaida.de("data", "Data (dd/mm/aaaa)", "string"),
            EspecSaida.de("hora", "Hora (hh:mm)", "string"),
            EspecSaida.de("dia_da_semana", "Dia da semana", "string"),
            EspecSaida.de("semana", "Semana do ano", "number"),
            EspecSaida.de("mes", "Mês", "number"),
            EspecSaida.de("ano", "Ano", "number"));

    private final NotificacaoApi notificacoes;
    private final EnriquecedorEntidades entidades;

    public NosGatilhos(NotificacaoApi notificacoes, EnriquecedorEntidades entidades) {
        this.notificacoes = notificacoes;
        this.entidades = entidades;
    }

    public static String rotulo(String variavel) {
        String r = ROTULOS.get(variavel);
        if (r != null) return r;
        String separado = variavel.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').toLowerCase();
        return separado.isEmpty() ? variavel : Character.toUpperCase(separado.charAt(0)) + separado.substring(1);
    }

    private static String tipoDaVariavel(String nome) {
        String n = nome.toLowerCase();
        if (n.startsWith("valor") || n.equals("total") || n.equals("limiar") || n.equals("parcelas") || n.startsWith("dias")
                || n.startsWith("total") || n.startsWith("prazoreabertura")) return "number";
        if (n.startsWith("com") || n.equals("obracivil") || n.equals("frota")) return "boolean";
        return "string";
    }

    static Map<String, Object> dataHora(OffsetDateTime momento) {
        ZonedDateTime local = momento.atZoneSameInstant(Valores.BRASILIA);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", Valores.formatarData(local, "dd/mm/aaaa"));
        m.put("hora", Valores.formatarData(local, "hh:mm"));
        m.put("dia_da_semana", Recorrencia.DIAS[local.getDayOfWeek().getValue() - 1]);
        m.put("semana", local.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        m.put("mes", local.getMonthValue());
        m.put("ano", local.getYear());
        return m;
    }

    @Override
    public Collection<EspecNo> nos() {
        List<EspecNo> nos = new ArrayList<>();
        nos.add(manual());
        nos.add(agenda());
        nos.add(falhaDeAutomacao());
        for (GatilhoCatalogado g : notificacoes.catalogoGatilhos()) {
            if ("LIMIAR".equals(g.modo())) nos.add(limiar(g));
            else nos.add(evento(g));
        }
        return nos;
    }

    private EspecNo manual() {
        List<EspecSaida> saidas = new ArrayList<>();
        saidas.add(EspecSaida.de("inputs", "Informações preenchidas", "object"));
        saidas.add(EspecSaida.objeto("executado_por", "Quem executou", Pessoa.CAMPOS));
        saidas.addAll(SAIDAS_DATA);
        return EspecNo.de("trigger.manual", "trigger", "Executar manualmente",
                        "Roda quando alguém clica em Executar no Nexo ou pede ao Copiloto. Pode pedir informações antes.", "play")
                .gatilho()
                .params(EspecParam.de("inputs", "Informações pedidas ao executar", "fields").padrao(List.of()).estatico()
                        .campos(EspecCampo.texto("name", "Nome (sem espaço)", "base"),
                                EspecCampo.texto("label", "Pergunta", "Qual base?"),
                                EspecCampo.selecao("type", "Tipo", List.of(List.of("text", "Texto"), List.of("longtext", "Texto longo"),
                                        List.of("number", "Número"), List.of("boolean", "Sim/Não"), List.of("date", "Data"),
                                        List.of("email", "E-mail"))),
                                EspecCampo.booleano("required", "Obrigatório"))
                        .criar())
                .saidas(saidas)
                .saidasDinamicas("inputs")
                .preparador((ctx, params, carga) -> {
                    Map<String, Object> dados = Valores.mapa(carga.get("inputs"));
                    Map<String, Object> inputs = new LinkedHashMap<>();
                    for (Object campo : Valores.lista(params.get("inputs"))) {
                        Map<String, Object> f = Valores.mapa(campo);
                        String nome = Valores.texto(f.get("name")).strip();
                        if (nome.isEmpty()) continue;
                        Object valor = dados.containsKey(nome) ? dados.get(nome) : f.get("default");
                        if (Valores.verdadeiro(f.get("required")) && Valores.vazio(valor)) {
                            throw new IllegalArgumentException("Preencha '" + (Valores.texto(f.get("label")).isBlank() ? nome : Valores.texto(f.get("label"))) + "'");
                        }
                        String tipo = Valores.texto(f.getOrDefault("type", "text"));
                        if (tipo.equals("number")) valor = Valores.numero(valor);
                        else if (tipo.equals("boolean")) valor = Valores.verdadeiro(valor);
                        inputs.put(nome, valor);
                    }
                    Map<String, Object> saida = new LinkedHashMap<>();
                    saida.put("inputs", inputs);
                    Object usuario = carga.get("usuario_id");
                    saida.put("executado_por", usuario == null ? null : entidades.pessoa(UUID.fromString(Valores.texto(usuario))));
                    saida.putAll(dataHora(ctx.agora()));
                    return saida;
                })
                .palavras("botão", "instantâneo", "manual", "executar")
                .criar();
    }

    private EspecNo agenda() {
        List<List<String>> dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) dias.add(List.of(String.valueOf(i), cap(Recorrencia.DIAS[i])));
        List<EspecSaida> saidas = new ArrayList<>();
        saidas.add(EspecSaida.de("agendado_para", "Horário agendado", "string"));
        saidas.addAll(SAIDAS_DATA);
        saidas.add(EspecSaida.de("semana_passada_inicio", "Semana passada: segunda (dd/mm/aaaa)", "string"));
        saidas.add(EspecSaida.de("semana_passada_fim", "Semana passada: domingo (dd/mm/aaaa)", "string"));
        saidas.add(EspecSaida.de("semana_atual_inicio", "Semana atual: segunda (dd/mm/aaaa)", "string"));
        saidas.add(EspecSaida.de("mes_passado_inicio", "Mês passado: primeiro dia", "string"));
        saidas.add(EspecSaida.de("mes_passado_fim", "Mês passado: último dia", "string"));
        saidas.add(EspecSaida.de("mes_atual_inicio", "Mês atual: primeiro dia", "string"));
        saidas.add(EspecSaida.de("ontem", "Ontem (dd/mm/aaaa)", "string"));
        return EspecNo.de("trigger.schedule", "trigger", "Agenda (recorrência)",
                        "Roda sozinha: a cada tantos minutos, horas, todo dia, em dias da semana ou num dia do mês (horário de Brasília).", "calendar")
                .gatilho()
                .params(
                        EspecParam.de("frequency", "Repetir", "select").padrao("week").estatico()
                                .opcoes(List.of(List.of("minute", "A cada X minutos"), List.of("hour", "A cada X horas"),
                                        List.of("day", "Todo dia"), List.of("week", "Em dias da semana"), List.of("month", "Todo mês")))
                                .criar(),
                        EspecParam.de("interval", "A cada", "number").padrao(1).estatico().faixa(1, 1000)
                                .mostrarSe("frequency", "minute", "hour", "day").criar(),
                        EspecParam.de("weekdays", "Dias", "weekdays").padrao(List.of(0)).estatico().opcoes(dias)
                                .mostrarSe("frequency", "week").criar(),
                        EspecParam.de("day_of_month", "Dia do mês", "number").padrao(1).estatico().faixa(1, 28)
                                .mostrarSe("frequency", "month").criar(),
                        EspecParam.de("time", "Horário (Brasília)", "time").padrao("08:00").estatico()
                                .mostrarSe("frequency", "day", "week", "month").criar())
                .saidas(saidas)
                .resumo(Recorrencia::descrever)
                .preparador((ctx, params, carga) -> {
                    Object bruto = carga.get("agendado_para");
                    OffsetDateTime momento = bruto == null ? ctx.agora() : OffsetDateTime.parse(Valores.texto(bruto));
                    Map<String, Object> saida = new LinkedHashMap<>();
                    saida.put("agendado_para", momento.toString());
                    saida.putAll(dataHora(momento));
                    ZonedDateTime local = momento.atZoneSameInstant(Valores.BRASILIA);
                    ZonedDateTime segunda = local.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    saida.put("semana_atual_inicio", Valores.formatarData(segunda, "dd/mm/aaaa"));
                    saida.put("semana_passada_inicio", Valores.formatarData(segunda.minusWeeks(1), "dd/mm/aaaa"));
                    saida.put("semana_passada_fim", Valores.formatarData(segunda.minusDays(1), "dd/mm/aaaa"));
                    ZonedDateTime primeiro = local.withDayOfMonth(1);
                    saida.put("mes_atual_inicio", Valores.formatarData(primeiro, "dd/mm/aaaa"));
                    saida.put("mes_passado_inicio", Valores.formatarData(primeiro.minusMonths(1), "dd/mm/aaaa"));
                    saida.put("mes_passado_fim", Valores.formatarData(primeiro.minusDays(1), "dd/mm/aaaa"));
                    saida.put("ontem", Valores.formatarData(local.minusDays(1), "dd/mm/aaaa"));
                    return saida;
                })
                .palavras("agendado", "recorrência", "semanal", "diário", "mensal", "cron", "toda segunda")
                .criar();
    }

    private EspecNo falhaDeAutomacao() {
        return EspecNo.de("trigger.automation_failed", "trigger", "Uma automação falhou",
                        "Roda quando outra automação termina com falha (não vale para testes).", "alert")
                .gatilho()
                .params(EspecParam.de("automations", "Só destas automações (vazio = qualquer uma)", "automations").padrao(List.of()).estatico().criar())
                .saidas(EspecSaida.de("automacao_nome", "Automação que falhou", "string"),
                        EspecSaida.de("erro", "Motivo da falha", "string"),
                        EspecSaida.de("link", "Link da execução", "string"),
                        EspecSaida.de("automacao_id", "Id da automação", "string"))
                .preparador((ctx, params, carga) -> {
                    List<Object> filtro = Valores.lista(params.get("automations"));
                    if (!filtro.isEmpty() && filtro.stream().noneMatch(f -> Valores.texto(f).equals(Valores.texto(carga.get("automacao_id"))))) {
                        return null;
                    }
                    return new LinkedHashMap<>(carga);
                })
                .palavras("erro", "falha", "monitorar")
                .criar();
    }

    private EspecNo limiar(GatilhoCatalogado g) {
        String prefixo = g.chave().split("\\.")[0];
        return EspecNo.de("threshold." + g.chave(), "trigger", g.nome(), g.descricao(), "gauge")
                .gatilho()
                .evento(null)
                .subgrupo(SUBGRUPOS.getOrDefault(prefixo, "Outros"))
                .params(
                        EspecParam.de("limiar", "Disparar a partir de", "number").obrigatorio().padrao(1).estatico().faixa(1, 1_000_000).criar(),
                        EspecParam.de("silencio_minutos", "Depois de avisar, esperar (minutos)", "number").padrao(240).estatico().faixa(0, 100_000)
                                .ajuda("Evita repetir o aviso a cada verificação enquanto a fila continua acima do limiar.").criar(),
                        EspecParam.de("verificar_minutos", "Verificar a cada (minutos)", "number").padrao(15).estatico().faixa(5, 1440).avancado().criar())
                .saidas(EspecSaida.de("total", "Total medido", "number"), EspecSaida.de("limiar", "Limiar configurado", "number"),
                        EspecSaida.de("data", "Data (dd/mm/aaaa)", "string"), EspecSaida.de("hora", "Hora (hh:mm)", "string"))
                .resumo(p -> "Quando chegar a " + Valores.texto(p.getOrDefault("limiar", 1)))
                .preparador((ctx, params, carga) -> {
                    Map<String, Object> saida = new LinkedHashMap<>();
                    saida.put("total", Valores.numero(carga.get("total")));
                    saida.put("limiar", Valores.numero(params.getOrDefault("limiar", 1)));
                    saida.putAll(dataHora(ctx.agora()));
                    return saida;
                })
                .palavras("fila", "pendentes", "acumulado", "limite")
                .criar();
    }

    private EspecNo evento(GatilhoCatalogado g) {
        String prefixo = g.chave().split("\\.")[0];
        List<EspecSaida> saidas = new ArrayList<>();
        for (String v : g.variaveis()) {
            saidas.add(EspecSaida.de(v, rotulo(v), tipoDaVariavel(v)));
        }
        boolean temColaborador = g.variaveis().contains("idColaborador");
        if (temColaborador) {
            saidas.add(0, EspecSaida.objeto("colaborador", "Colaborador", colaboradorCampos()));
        }
        for (String papel : g.papeis()) saidas.add(EspecSaida.objeto(papel, rotulo(papel), Pessoa.CAMPOS));
        if (!g.papeis().isEmpty()) {
            List<EspecSaida> listas = new ArrayList<>();
            for (String papel : g.papeis()) listas.add(EspecSaida.lista(papel, rotulo(papel) + " (todos)", Pessoa.CAMPOS));
            saidas.add(EspecSaida.objeto("papeis", "Pessoas do evento", listas));
        }
        saidas.add(EspecSaida.de("id_referencia", "Id do registro", "string"));
        saidas.addAll(SAIDAS_DATA.subList(0, 2));
        return EspecNo.de("event." + g.chave(), "trigger", g.nome(), g.descricao(), "bolt")
                .gatilho()
                .evento(g.chave())
                .subgrupo(SUBGRUPOS.getOrDefault(prefixo, "Outros"))
                .params(EspecParam.de("filter", "Só quando", "condition").padrao(Map.of("op", "and", "rules", List.of()))
                        .semResolver().avancado()
                        .ajuda("Condição sobre os dados do evento; quando falsa, nem cria execução (evita histórico de execuções vazias).")
                        .criar())
                .saidas(saidas)
                .preparador((ctx, params, carga) -> {
                    Map<String, Object> contexto = Valores.mapa(carga.get("contexto"));
                    Map<String, Object> saida = new LinkedHashMap<>();
                    contexto.forEach((k, v) -> saida.put(k, Valores.comoJson(v)));
                    if (temColaborador) {
                        Object id = contexto.get("idColaborador");
                        saida.put("colaborador", id == null ? null : entidades.colaborador(UUID.fromString(Valores.texto(id))));
                    }
                    Map<String, Object> papeis = Valores.mapa(carga.get("papeis"));
                    Map<String, Object> pessoasPorPapel = new LinkedHashMap<>();
                    for (String papel : g.papeis()) {
                        List<UUID> ids = new ArrayList<>();
                        for (Object id : Valores.lista(papeis.get(papel))) ids.add(UUID.fromString(Valores.texto(id)));
                        List<Map<String, Object>> pessoas = entidades.pessoas(ids);
                        pessoasPorPapel.put(papel, pessoas);
                        saida.put(papel, pessoas.isEmpty() ? null : pessoas.get(0));
                    }
                    if (!g.papeis().isEmpty()) saida.put("papeis", pessoasPorPapel);
                    saida.put("id_referencia", carga.get("id_referencia"));
                    saida.putAll(dataHora(ctx.agora()));
                    Object filtro = params.get("filter");
                    if (filtro != null && !Condicoes.ler(filtro).regras().isEmpty()) {
                        Map<String, Object> escopo = Map.of("trigger", Map.of("outputs", saida, "type", "event." + g.chave()));
                        if (!Condicoes.avaliar(filtro, new java.util.HashMap<>(escopo))) return null;
                    }
                    return saida;
                })
                .palavras(g.chave().replace('.', ' ').replace('_', ' '), "evento", "quando")
                .criar();
    }

    private static List<EspecSaida> colaboradorCampos() {
        List<EspecSaida> campos = new ArrayList<>(Pessoa.CAMPOS);
        campos.add(EspecSaida.de("ativo", "Ativo", "boolean"));
        return campos;
    }

    private static String cap(String texto) {
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
