package com.nexo.app.modules.automacao.application.nos;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nexo.app.modules.assistente.CopilotoApi;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecCampo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecNo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecParam;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecSaida;
import com.nexo.app.modules.automacao.application.motor.Catalogo.ProvedorNos;
import com.nexo.app.modules.automacao.application.motor.ContextoAcao;
import com.nexo.app.modules.automacao.application.motor.ErroAcao;
import com.nexo.app.modules.automacao.application.usecases.EnriquecedorEntidades;
import com.nexo.app.modules.automacao.application.usecases.Pessoa;
import com.nexo.app.modules.automacao.application.usecases.ResolvedorDestinatarios;
import com.nexo.app.modules.automacao.domain.TipoAutomacao;
import com.nexo.app.modules.automacao.domain.expressao.Valores;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;

/**
 * Pessoas e dados do Nexo: escolher destinatários por regra (ex.: a base do
 * colaborador decide qual RH atende), buscar colaborador ou usuário, consultar os dados
 * do sistema com as permissões do dono e pedir aprovação.
 */
@Component
public class NosPessoasDados implements ProvedorNos {

    private final ResolvedorDestinatarios destinatarios;
    private final EnriquecedorEntidades entidades;
    private final ColaboradorApi colaboradores;
    private final UsuarioApi usuarios;
    private final CopilotoApi copiloto;

    public NosPessoasDados(ResolvedorDestinatarios destinatarios, EnriquecedorEntidades entidades, ColaboradorApi colaboradores,
                           UsuarioApi usuarios, CopilotoApi copiloto) {
        this.destinatarios = destinatarios;
        this.entidades = entidades;
        this.colaboradores = colaboradores;
        this.usuarios = usuarios;
        this.copiloto = copiloto;
    }

    @Override
    public Collection<EspecNo> nos() {
        List<EspecNo> nos = new ArrayList<>();
        nos.add(EspecNo.de("people.route", "people", "Escolher destinatários por regra",
                        "Uma tabela de quem recebe conforme um valor — ex.: se a base do colaborador for Juazeiro, o RH de Juazeiro; "
                                + "se for Petrolina, o RH de Petrolina; senão, todo o RH. Use a saída em 'Para' ou 'Quem recebe'.", "route")
                .params(
                        EspecParam.de("value", "Valor que decide", "expression").obrigatorio()
                                .placeholder("Escolha um dado, ex.: Colaborador › Base operacional").criar(),
                        EspecParam.de("routes", "Regras", "routes").obrigatorio().padrao(List.of())
                                .ajuda("Em 'Quando for', separe vários valores com ; (ex.: Juazeiro; Juazeiro II).").criar(),
                        EspecParam.de("fallback", "Senão (quando nenhuma regra servir)", "recipients").padrao(List.of()).criar(),
                        EspecParam.de("match", "Se mais de uma regra servir", "select").padrao("first").estatico()
                                .opcoes(List.of(List.of("first", "Usar só a primeira"), List.of("all", "Somar todas"))).avancado().criar(),
                        EspecParam.de("compare", "Comparar", "select").padrao("eq").estatico()
                                .opcoes(List.of(List.of("eq", "Igual (sem diferenciar maiúsculas)"), List.of("contains", "Contém"),
                                        List.of("starts_with", "Começa com"))).avancado().criar())
                .saidas(EspecSaida.de("recipients", "Destinatários escolhidos", "array",
                                "Use em 'Para' (e-mail) ou 'Quem recebe' (aviso)"),
                        EspecSaida.lista("people", "Pessoas escolhidas", Pessoa.CAMPOS),
                        EspecSaida.de("emails", "E-mails", "array"),
                        EspecSaida.de("matched", "Regra usada", "string"),
                        EspecSaida.de("found", "Alguma regra serviu", "boolean"))
                .executor(this::rotear)
                .palavras("rotear", "roteamento", "base", "destinatário", "quem recebe", "política", "rh", "regional")
                .criar());
        nos.add(EspecNo.de("ces.collaborator", "ces", "Buscar colaborador",
                        "Traz nome, base, função, centro de custo e o usuário (e-mail) de um colaborador ou de um usuário.", "user")
                .params(EspecParam.de("by", "Buscar por", "select").padrao("colaborador").estatico()
                                .opcoes(List.of(List.of("colaborador", "Id do colaborador"), List.of("usuario", "Id do usuário"),
                                        List.of("email", "E-mail do usuário"))).criar(),
                        EspecParam.de("value", "Valor", "text").obrigatorio().placeholder("Escolha um dado de um passo anterior").criar())
                .saidas(saidasPessoa())
                .executor(this::buscarColaborador)
                .palavras("colaborador", "pessoa", "usuário", "base", "função", "funcionário")
                .criar());
        nos.add(EspecNo.de("ces.people", "ces", "Listar pessoas",
                        "Lista as pessoas com uma permissão, de um perfil ou de uma base — com e-mail, base e função.", "users")
                .params(EspecParam.de("recipients", "Quem", "recipients").obrigatorio().padrao(List.of()).criar())
                .saidas(EspecSaida.lista("people", "Pessoas", Pessoa.CAMPOS), EspecSaida.de("emails", "E-mails", "array"),
                        EspecSaida.de("count", "Quantidade", "number"))
                .executor((ctx, p) -> {
                    ResolvedorDestinatarios.Resolucao r = destinatarios.resolverTudo(p.get("recipients"), ctx);
                    Map<String, Object> saida = new LinkedHashMap<>();
                    List<Map<String, Object>> pessoas = entidades.pessoas(r.usuarios());
                    saida.put("people", pessoas);
                    saida.put("emails", new ArrayList<>(destinatarios.emails(r)));
                    saida.put("count", pessoas.size() + r.emailsExternos().size());
                    return saida;
                })
                .palavras("pessoas", "usuários", "equipe", "lista", "permissão", "perfil")
                .criar());
        nos.add(EspecNo.de("ces.query", "ces", "Consultar dados do Nexo",
                        "Consulta estruturada às bases de dados do Nexo (descontos, execução, estoque, SST…), com as permissões do responsável.", "database")
                .params(
                        EspecParam.de("source", "Fonte de dados", "source").obrigatorio().estatico().criar(),
                        EspecParam.de("columns", "Colunas (vazio = todas)", "list").padrao(List.of()).criar(),
                        EspecParam.de("group_by", "Agrupar por", "list").padrao(List.of()).criar(),
                        EspecParam.de("metrics", "Cálculos", "fields").padrao(List.of())
                                .campos(EspecCampo.selecao("funcao", "Cálculo", List.of(List.of("contagem", "Contagem"),
                                                List.of("contagem_distinta", "Contagem distinta"), List.of("soma", "Soma"),
                                                List.of("media", "Média"), List.of("minimo", "Mínimo"), List.of("maximo", "Máximo"))),
                                        EspecCampo.texto("coluna", "Coluna", "valor"), EspecCampo.texto("alias", "Nome do resultado", "total"))
                                .criar(),
                        EspecParam.de("filters", "Filtros", "fields").padrao(List.of())
                                .campos(EspecCampo.texto("coluna", "Coluna", "base_operacional"),
                                        EspecCampo.selecao("operador", "Operador", List.of(List.of("igual", "é igual a"),
                                                List.of("diferente", "é diferente de"), List.of("em", "é um de (;)"), List.of("contem", "contém"),
                                                List.of("maior", "maior que"), List.of("maior_igual", "maior ou igual"),
                                                List.of("menor", "menor que"), List.of("menor_igual", "menor ou igual"),
                                                List.of("nulo", "está vazio"), List.of("nao_nulo", "não está vazio"))),
                                        EspecCampo.expressao("valor", "Valor", "Juazeiro"))
                                .criar(),
                        EspecParam.de("period", "Período", "select").padrao("none").estatico()
                                .opcoes(List.of(List.of("none", "Sem período"), List.of("last_week", "Semana passada"),
                                        List.of("this_week", "Esta semana"), List.of("last_month", "Mês passado"),
                                        List.of("this_month", "Este mês"), List.of("last_7", "Últimos 7 dias"),
                                        List.of("last_30", "Últimos 30 dias"), List.of("custom", "Datas informadas"))).criar(),
                        EspecParam.de("period_column", "Coluna da data", "text").placeholder("Vazio = a coluna de data da fonte")
                                .mostrarSe("period", "last_week", "this_week", "last_month", "this_month", "last_7", "last_30", "custom").avancado().criar(),
                        EspecParam.de("start", "De (dd/mm/aaaa)", "text").mostrarSe("period", "custom").criar(),
                        EspecParam.de("end", "Até (dd/mm/aaaa)", "text").mostrarSe("period", "custom").criar(),
                        EspecParam.de("order_by", "Ordenar por", "text").avancado().criar(),
                        EspecParam.de("desc", "Do maior para o menor", "boolean").padrao(true).estatico().avancado().criar(),
                        EspecParam.de("limit", "Máximo de linhas", "number").padrao(500).estatico().faixa(1, 5000).avancado().criar())
                .saidas(EspecSaida.de("rows", "Linhas", "array"), EspecSaida.de("count", "Quantidade de linhas", "number"),
                        EspecSaida.de("columns", "Colunas", "array"), EspecSaida.de("truncated", "Resultado cortado", "boolean"),
                        EspecSaida.de("period_label", "Período consultado", "string"))
                .executor(this::consultar)
                .repeticao("fixed", 1, 10)
                .satisfaz(TipoAutomacao.DADOS_IA)
                .palavras("consulta", "dados", "sql", "relatório", "produtividade", "indicador", "buscar")
                .criar());
        nos.add(EspecNo.de("approval.request", "approval", "Pedir aprovação",
                        "Pede a decisão de uma pessoa (no sininho e por e-mail) e espera a resposta antes de seguir. O primeiro botão conta como aprovado.", "check")
                .params(
                        EspecParam.de("title", "O que aprovar", "text").obrigatorio().placeholder("Liberar o envio do relatório?").criar(),
                        EspecParam.de("details", "Detalhes", "textarea").placeholder("Inclua os dados dos passos anteriores").criar(),
                        EspecParam.de("approvers", "Quem aprova", "recipients").obrigatorio().padrao(List.of()).criar(),
                        EspecParam.de("kind", "Respostas", "select").padrao("approve_reject").estatico()
                                .opcoes(List.of(List.of("approve_reject", "Aprovar ou recusar"), List.of("custom", "Opções próprias"))).criar(),
                        EspecParam.de("options", "Opções", "list").padrao(List.of()).mostrarSe("kind", "custom").criar(),
                        EspecParam.de("expires_after_hours", "Prazo (horas, 0 = sem prazo)", "number").padrao(48).faixa(0, 2000).criar())
                .saidas(EspecSaida.de("outcome", "Resposta", "string"), EspecSaida.de("approved", "Aprovado", "boolean"),
                        EspecSaida.de("responder", "Quem respondeu", "string"), EspecSaida.de("responder_email", "E-mail de quem respondeu", "string"),
                        EspecSaida.de("comment", "Comentário", "string"), EspecSaida.de("decided_at", "Respondido em", "string"))
                .satisfaz(TipoAutomacao.APROVACAO)
                .palavras("aprovar", "aprovação", "decisão", "autorizar")
                .criar());
        return nos;
    }

    private static List<EspecSaida> saidasPessoa() {
        List<EspecSaida> saidas = new ArrayList<>(Pessoa.CAMPOS);
        saidas.add(EspecSaida.de("found", "Encontrado", "boolean"));
        return saidas;
    }

    // ------------------------------------------------------------------------------ rotear

    private Map<String, Object> rotear(ContextoAcao ctx, Map<String, Object> p) {
        String valor = Valores.comparavel(p.get("value"));
        String comparar = Valores.texto(p.getOrDefault("compare", "eq"));
        boolean todas = "all".equals(Valores.texto(p.get("match")));
        List<Object> escolhidos = new ArrayList<>();
        List<String> usadas = new ArrayList<>();
        for (Object bruta : Valores.lista(p.get("routes"))) {
            Map<String, Object> rota = Valores.mapa(bruta);
            boolean serve = false;
            for (String opcao : Valores.texto(rota.get("when")).split(";")) {
                String o = Valores.comparavel(opcao);
                if (o.isEmpty()) continue;
                serve = switch (comparar) {
                    case "contains" -> valor.contains(o);
                    case "starts_with" -> valor.startsWith(o);
                    default -> valor.equals(o);
                };
                if (serve) break;
            }
            if (!serve) continue;
            escolhidos.addAll(Valores.lista(rota.get("recipients")));
            usadas.add(Valores.texto(rota.get("when")));
            if (!todas) break;
        }
        boolean achou = !usadas.isEmpty();
        if (!achou) escolhidos.addAll(Valores.lista(p.get("fallback")));
        ResolvedorDestinatarios.Resolucao r = destinatarios.resolverTudo(escolhidos, ctx);
        // Saída canônica: ids e e-mails, já resolvidos (permissão e perfil viram pessoas aqui).
        List<Object> canonicos = new ArrayList<>();
        Set<UUID> ids = new LinkedHashSet<>(r.usuarios());
        for (UUID id : ids) canonicos.add(Map.of("tipo", "usuario", "valor", id.toString()));
        for (String e : r.emailsExternos()) canonicos.add(Map.of("tipo", "email", "valor", e));
        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("recipients", canonicos);
        saida.put("people", entidades.pessoas(ids));
        saida.put("emails", new ArrayList<>(destinatarios.emails(r)));
        saida.put("matched", achou ? String.join(" + ", usadas) : "Senão");
        saida.put("found", achou);
        return saida;
    }

    // ------------------------------------------------------------------------------ pessoas

    private Map<String, Object> buscarColaborador(ContextoAcao ctx, Map<String, Object> p) {
        String por = Valores.texto(p.getOrDefault("by", "colaborador"));
        String valor = Valores.texto(p.get("value")).strip();
        Map<String, Object> pessoa = null;
        try {
            switch (por) {
                case "usuario" -> pessoa = entidades.pessoa(UUID.fromString(valor));
                case "email" -> pessoa = usuarios.buscarIdAtivoPorEmail(valor).map(entidades::pessoa).orElse(null);
                default -> {
                    UUID id = UUID.fromString(valor);
                    pessoa = entidades.colaborador(id);
                    if (pessoa == null) {
                        // Talvez seja o id do usuário.
                        ColaboradorResponse c = colaboradores.buscarPorIdResponse(id).orElse(null);
                        if (c == null) pessoa = entidades.pessoa(id);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw new ErroAcao("Valor inválido para buscar: " + valor);
        }
        Map<String, Object> saida = new LinkedHashMap<>();
        if (pessoa != null) saida.putAll(pessoa);
        else for (EspecSaida s : Pessoa.CAMPOS) saida.put(s.chave(), null);
        saida.put("found", pessoa != null);
        return saida;
    }

    // ------------------------------------------------------------------------------ consulta

    private Map<String, Object> consultar(ContextoAcao ctx, Map<String, Object> p) {
        String fonte = Valores.texto(p.get("source")).strip();
        if (fonte.isEmpty()) throw new ErroAcao("Escolha a fonte de dados");
        List<CopilotoApi.Metrica> metricas = new ArrayList<>();
        for (Object m : Valores.lista(p.get("metrics"))) {
            Map<String, Object> mm = Valores.mapa(m);
            if (Valores.texto(mm.get("funcao")).isBlank()) continue;
            metricas.add(new CopilotoApi.Metrica(Valores.texto(mm.get("funcao")), vazioNulo(mm.get("coluna")), vazioNulo(mm.get("alias"))));
        }
        List<CopilotoApi.Filtro> filtros = new ArrayList<>();
        for (Object f : Valores.lista(p.get("filters"))) {
            Map<String, Object> ff = Valores.mapa(f);
            String coluna = Valores.texto(ff.get("coluna")).strip();
            if (coluna.isEmpty()) continue;
            String operador = Valores.texto(ff.getOrDefault("operador", "igual"));
            Object valor = ctx.resolver(ff.get("valor"));
            List<Object> valores = new ArrayList<>();
            if (valor instanceof List<?> l) valores.addAll(l);
            else if (operador.equals("em")) for (String parte : Valores.texto(valor).split(";")) valores.add(parte.strip());
            else if (!operador.equals("nulo") && !operador.equals("nao_nulo")) valores.add(valor);
            filtros.add(new CopilotoApi.Filtro(coluna, operador, valores));
        }
        LocalDate[] periodo = periodo(Valores.texto(p.getOrDefault("period", "none")), p, ctx);
        String colunaData = Valores.texto(p.get("period_column")).strip();
        if (periodo != null && colunaData.isEmpty()) {
            colunaData = copiloto.fontes(ctx.donoPermissoes()).stream().filter(f -> f.id().equals(fonte)).findFirst()
                    .map(CopilotoApi.FonteDados::colunaData).orElse(null);
            if (colunaData == null) throw new ErroAcao("A fonte não tem coluna de data padrão: informe a coluna do período");
        }
        CopilotoApi.ResultadoConsulta resultado;
        try {
            resultado = copiloto.consultar(ctx.exigirDono(), ctx.donoPermissoes(), new CopilotoApi.Consulta(fonte,
                    textos(p.get("columns")), textos(p.get("group_by")), metricas, filtros, periodo == null ? null : colunaData,
                    periodo == null ? null : periodo[0], periodo == null ? null : periodo[1], vazioNulo(p.get("order_by")),
                    !Boolean.FALSE.equals(p.get("desc")), (int) Math.max(1, Math.min(5000, Valores.dbl(p.getOrDefault("limit", 500))))));
        } catch (RuntimeException e) {
            throw new ErroAcao("A consulta falhou: " + e.getMessage());
        }
        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("rows", resultado.linhas());
        saida.put("count", resultado.linhas().size());
        saida.put("columns", resultado.colunas());
        saida.put("truncated", resultado.truncado());
        saida.put("period_label", periodo == null ? "" : Valores.formatarData(periodo[0], "dd/mm/aaaa") + " a " + Valores.formatarData(periodo[1], "dd/mm/aaaa"));
        return saida;
    }

    private static LocalDate[] periodo(String tipo, Map<String, Object> p, ContextoAcao ctx) {
        LocalDate hoje = ctx.agora().atZoneSameInstant(Valores.BRASILIA).toLocalDate();
        LocalDate segunda = hoje.minusDays(hoje.getDayOfWeek().getValue() - 1L);
        return switch (tipo) {
            case "last_week" -> new LocalDate[]{segunda.minusWeeks(1), segunda.minusDays(1)};
            case "this_week" -> new LocalDate[]{segunda, hoje};
            case "last_month" -> new LocalDate[]{hoje.withDayOfMonth(1).minusMonths(1), hoje.withDayOfMonth(1).minusDays(1)};
            case "this_month" -> new LocalDate[]{hoje.withDayOfMonth(1), hoje};
            case "last_7" -> new LocalDate[]{hoje.minusDays(7), hoje.minusDays(1)};
            case "last_30" -> new LocalDate[]{hoje.minusDays(30), hoje.minusDays(1)};
            case "custom" -> {
                ZonedDateTime de = Valores.instante(p.get("start"));
                ZonedDateTime ate = Valores.instante(p.get("end"));
                if (de == null || ate == null) throw new ErroAcao("Informe as datas do período (dd/mm/aaaa)");
                yield new LocalDate[]{de.toLocalDate(), ate.toLocalDate()};
            }
            default -> null;
        };
    }

    private static List<String> textos(Object valor) {
        List<String> lista = new ArrayList<>();
        for (Object v : Valores.lista(valor)) if (!Valores.texto(v).isBlank()) lista.add(Valores.texto(v).strip());
        return lista;
    }

    private static String vazioNulo(Object valor) {
        String t = Valores.texto(valor).strip();
        return t.isEmpty() ? null : t;
    }
}
