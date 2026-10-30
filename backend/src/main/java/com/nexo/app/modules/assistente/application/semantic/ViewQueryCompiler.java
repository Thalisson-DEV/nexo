package com.nexo.app.modules.assistente.application.semantic;

import com.nexo.app.modules.assistente.application.agent.ToolException;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Compila {@link ViewQuery} em SQL parametrizado. Todo identificador e validado contra as colunas reais da
 * view (information_schema) e todo valor vira parametro JDBC: nenhum texto do modelo entra no SQL.
 */
@Component
public class ViewQueryCompiler {

    public static final int LIMITE_PADRAO = 200;
    public static final int LIMITE_MAXIMO = 5_000;
    private static final Set<String> FUNCOES = Set.of("contagem", "contagem_distinta", "soma", "media", "minimo",
            "maximo", "mediana");
    private static final Set<String> NUMERICOS = Set.of("integer", "bigint", "smallint", "numeric", "double precision",
            "real", "decimal");

    private final SemanticCatalog catalog;

    public ViewQueryCompiler(SemanticCatalog catalog) {
        this.catalog = catalog;
    }

    public boolean autorizado(String fonteId, SemanticQueryContext contexto) {
        return catalog.fonte(fonteId).map(fonte -> catalog.autorizado(fonte, contexto)).orElse(false);
    }

    public CompiledQuery compilar(ViewQuery consulta, SemanticQueryContext contexto) {
        SemanticCatalog.Fonte fonte = catalog.fonte(consulta.fonte()).orElseThrow(() -> new ToolException(
                "Fonte '" + consulta.fonte() + "' nao existe. Use listar_fontes_dados para ver as fontes disponiveis."));
        if (!catalog.autorizado(fonte, contexto)) {
            throw new ToolException("Seu perfil nao tem acesso a '" + fonte.nome()
                    + "'. Explique a limitacao sem revelar detalhes de permissao.");
        }
        Map<String, String> colunas = new LinkedHashMap<>();
        catalog.colunasDoBanco(fonte.id()).forEach(coluna -> colunas.put(coluna.nome(), coluna.tipo()));
        if (colunas.isEmpty()) {
            throw new ToolException("A camada semantica nao esta disponivel neste ambiente.");
        }

        List<Object> parametros = new ArrayList<>();
        List<String> where = new ArrayList<>();
        for (ViewQuery.Filtro filtro : consulta.filtros()) where.add(predicado(filtro, colunas, parametros));
        if (consulta.periodo() != null) where.addAll(periodo(consulta.periodo(), fonte, colunas, parametros));

        int limite = consulta.limite() == null ? LIMITE_PADRAO : Math.max(1, Math.min(consulta.limite(), LIMITE_MAXIMO));
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> selecionados = new ArrayList<>();
        Map<String, String> aliases = new LinkedHashMap<>();

        if (consulta.agregada()) {
            for (String dimensao : consulta.agruparPor()) {
                String coluna = exigirColuna(dimensao, colunas);
                selecionados.add(quote(coluna));
                aliases.put(coluna, quote(coluna));
            }
            List<ViewQuery.Metrica> metricas = consulta.metricas().isEmpty()
                    ? List.of(new ViewQuery.Metrica("contagem", null, "total"))
                    : consulta.metricas();
            Set<String> aliasesUsados = new HashSet<>();
            for (ViewQuery.Metrica metrica : metricas) {
                String funcao = metrica.funcao() == null ? "contagem" : metrica.funcao().trim().toLowerCase(Locale.ROOT);
                if (!FUNCOES.contains(funcao)) {
                    throw new ToolException("Metrica '" + metrica.funcao() + "' invalida. Use: " + String.join(", ", FUNCOES));
                }
                String alias = alias(metrica, funcao);
                if (!aliasesUsados.add(alias)) alias = alias + "_" + aliasesUsados.size();
                selecionados.add(expressaoMetrica(funcao, metrica.coluna(), colunas) + " AS " + quote(alias));
                aliases.put(alias, quote(alias));
            }
            sql.append(String.join(", ", selecionados)).append(" FROM ").append(fonte.nomeQualificado());
            if (!where.isEmpty()) sql.append(" WHERE ").append(String.join(" AND ", where));
            if (!consulta.agruparPor().isEmpty()) {
                sql.append(" GROUP BY ").append(String.join(", ", consulta.agruparPor().stream()
                        .map(dimensao -> quote(exigirColuna(dimensao, colunas))).toList()));
            }
            sql.append(ordenacao(consulta.ordenarPor(), aliases, colunas,
                    consulta.agruparPor().isEmpty() ? null : quote(aliasPrincipal(metricas)) + " DESC"));
        } else {
            List<String> detalhe = consulta.colunas().isEmpty() ? new ArrayList<>(colunas.keySet()) : consulta.colunas();
            for (String nome : detalhe) {
                String coluna = exigirColuna(nome, colunas);
                selecionados.add(quote(coluna));
                aliases.put(coluna, quote(coluna));
            }
            sql.append(String.join(", ", selecionados)).append(" FROM ").append(fonte.nomeQualificado());
            if (!where.isEmpty()) sql.append(" WHERE ").append(String.join(" AND ", where));
            String padrao = fonte.colunaData() != null && colunas.containsKey(fonte.colunaData())
                    ? quote(fonte.colunaData()) + " DESC NULLS LAST" : null;
            sql.append(ordenacao(consulta.ordenarPor(), aliases, colunas, padrao));
        }
        sql.append(" LIMIT ").append(limite + 1);
        return new CompiledQuery(sql.toString(), List.copyOf(parametros), fonte, limite);
    }

    private String predicado(ViewQuery.Filtro filtro, Map<String, String> colunas, List<Object> parametros) {
        String coluna = exigirColuna(filtro.coluna(), colunas);
        String tipo = colunas.get(coluna);
        String alvo = quote(coluna);
        String operador = filtro.operador() == null ? "igual" : filtro.operador().trim().toLowerCase(Locale.ROOT);
        List<Object> valores = filtro.valores();
        return switch (operador) {
            case "nulo" -> alvo + " IS NULL";
            case "nao_nulo" -> alvo + " IS NOT NULL";
            case "contem" -> {
                exigirValores(filtro, 1, 20);
                List<String> partes = new ArrayList<>();
                for (Object valor : valores) {
                    parametros.add("%" + escaparLike(String.valueOf(valor).toLowerCase(Locale.ROOT)) + "%");
                    partes.add("lower(CAST(" + alvo + " AS text)) LIKE ? ESCAPE '\\'");
                }
                yield partes.size() == 1 ? partes.getFirst() : "(" + String.join(" OR ", partes) + ")";
            }
            case "em", "nao_em" -> {
                exigirValores(filtro, 1, 100);
                List<String> marcadores = new ArrayList<>();
                for (Object valor : valores) {
                    parametros.add(converter(valor, tipo, coluna));
                    marcadores.add(marcador(tipo));
                }
                yield alvo + ("nao_em".equals(operador) ? " NOT IN (" : " IN (") + String.join(", ", marcadores) + ")";
            }
            case "entre" -> {
                exigirValores(filtro, 2, 2);
                parametros.add(converter(valores.get(0), tipo, coluna));
                parametros.add(converter(valores.get(1), tipo, coluna));
                yield alvo + " BETWEEN " + marcador(tipo) + " AND " + marcador(tipo);
            }
            case "igual", "diferente", "maior", "maior_igual", "menor", "menor_igual" -> {
                exigirValores(filtro, 1, 1);
                parametros.add(converter(valores.getFirst(), tipo, coluna));
                String simbolo = switch (operador) {
                    case "igual" -> "=";
                    case "diferente" -> "<>";
                    case "maior" -> ">";
                    case "maior_igual" -> ">=";
                    case "menor" -> "<";
                    default -> "<=";
                };
                yield alvo + " " + simbolo + " " + marcador(tipo);
            }
            default -> throw new ToolException("Operador de filtro '" + filtro.operador() + "' invalido. Use: igual, "
                    + "diferente, em, nao_em, contem, maior, maior_igual, menor, menor_igual, entre, nulo, nao_nulo.");
        };
    }

    private List<String> periodo(ViewQuery.Periodo periodo, SemanticCatalog.Fonte fonte, Map<String, String> colunas,
            List<Object> parametros) {
        String nome = periodo.coluna() == null || periodo.coluna().isBlank() ? fonte.colunaData() : periodo.coluna();
        if (nome == null) throw new ToolException("Informe a coluna de data do periodo para a fonte '" + fonte.id() + "'.");
        String coluna = exigirColuna(nome, colunas);
        String tipo = colunas.get(coluna);
        String alvo = tipo.startsWith("timestamp")
                ? "(" + quote(coluna) + " AT TIME ZONE 'America/Sao_Paulo')::date"
                : quote(coluna);
        if (!(tipo.startsWith("timestamp") || "date".equals(tipo))) {
            throw new ToolException("A coluna '" + coluna + "' nao e uma data.");
        }
        List<String> predicados = new ArrayList<>();
        if (periodo.inicio() != null) {
            parametros.add(java.sql.Date.valueOf(periodo.inicio()));
            predicados.add(alvo + " >= ?");
        }
        if (periodo.fim() != null) {
            parametros.add(java.sql.Date.valueOf(periodo.fim()));
            predicados.add(alvo + " <= ?");
        }
        if (periodo.inicio() != null && periodo.fim() != null && periodo.fim().isBefore(periodo.inicio())) {
            throw new ToolException("O fim do periodo e anterior ao inicio.");
        }
        return predicados;
    }

    private String expressaoMetrica(String funcao, String coluna, Map<String, String> colunas) {
        if ("contagem".equals(funcao) && (coluna == null || coluna.isBlank() || "*".equals(coluna))) return "count(*)";
        String nome = exigirColuna(coluna, colunas);
        String alvo = quote(nome);
        boolean numerico = NUMERICOS.contains(colunas.get(nome));
        return switch (funcao) {
            case "contagem" -> "count(" + alvo + ")";
            case "contagem_distinta" -> "count(DISTINCT " + alvo + ")";
            case "soma" -> exigirNumerico(numerico, nome, "coalesce(sum(" + alvo + "), 0)");
            case "media" -> exigirNumerico(numerico, nome, "round(avg(" + alvo + ")::numeric, 4)");
            case "mediana" -> exigirNumerico(numerico, nome,
                    "round((percentile_cont(0.5) WITHIN GROUP (ORDER BY " + alvo + "))::numeric, 4)");
            case "minimo" -> "min(" + alvo + ")";
            case "maximo" -> "max(" + alvo + ")";
            default -> throw new ToolException("Metrica invalida: " + funcao);
        };
    }

    private String ordenacao(List<ViewQuery.Ordem> ordens, Map<String, String> aliases, Map<String, String> colunas,
            String padrao) {
        List<String> partes = new ArrayList<>();
        for (ViewQuery.Ordem ordem : ordens) {
            if (ordem.campo() == null || ordem.campo().isBlank()) continue;
            String campo = ordem.campo().trim().toLowerCase(Locale.ROOT);
            String expressao = aliases.get(campo);
            if (expressao == null) {
                if (!aliases.isEmpty() && !colunas.containsKey(campo)) {
                    throw new ToolException("Ordenacao por '" + ordem.campo() + "' invalida: use uma coluna selecionada ou o alias de uma metrica.");
                }
                expressao = quote(exigirColuna(campo, colunas));
            }
            partes.add(expressao + (ordem.decrescente() ? " DESC NULLS LAST" : " ASC NULLS LAST"));
        }
        if (partes.isEmpty() && padrao != null) partes.add(padrao);
        return partes.isEmpty() ? "" : " ORDER BY " + String.join(", ", partes);
    }

    private static String alias(ViewQuery.Metrica metrica, String funcao) {
        if (metrica.alias() != null && metrica.alias().matches("[a-z][a-z0-9_]{0,40}")) return metrica.alias();
        if (metrica.coluna() == null || metrica.coluna().isBlank() || "*".equals(metrica.coluna())) return "total";
        String coluna = metrica.coluna().trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return funcao + "_" + coluna;
    }

    private static String aliasPrincipal(List<ViewQuery.Metrica> metricas) {
        ViewQuery.Metrica primeira = metricas.getFirst();
        String funcao = primeira.funcao() == null ? "contagem" : primeira.funcao().trim().toLowerCase(Locale.ROOT);
        return alias(primeira, funcao);
    }

    private static String exigirNumerico(boolean numerico, String coluna, String expressao) {
        if (!numerico) throw new ToolException("A coluna '" + coluna + "' nao e numerica para esta metrica.");
        return expressao;
    }

    private static String exigirColuna(String nome, Map<String, String> colunas) {
        if (nome == null) throw new ToolException("Coluna nao informada.");
        String normalizado = nome.trim().toLowerCase(Locale.ROOT);
        if (!normalizado.matches("[a-z_][a-z0-9_]*") || !colunas.containsKey(normalizado)) {
            throw new ToolException("Coluna '" + nome + "' nao existe nesta fonte. Colunas disponiveis: "
                    + String.join(", ", colunas.keySet()));
        }
        return normalizado;
    }

    private static void exigirValores(ViewQuery.Filtro filtro, int minimo, int maximo) {
        int quantidade = filtro.valores().size();
        if (quantidade < minimo || quantidade > maximo) {
            throw new ToolException("Filtro '" + filtro.coluna() + "' com operador '" + filtro.operador()
                    + "' exige entre " + minimo + " e " + maximo + " valores.");
        }
    }

    private static String marcador(String tipo) {
        return switch (tipo) {
            case "uuid" -> "CAST(? AS uuid)";
            case "date" -> "CAST(? AS date)";
            default -> tipo.startsWith("timestamp") ? "CAST(? AS timestamptz)" : "?";
        };
    }

    private static Object converter(Object valor, String tipo, String coluna) {
        if (valor == null) throw new ToolException("Valor nulo no filtro de '" + coluna + "'. Use o operador nulo.");
        String texto = String.valueOf(valor).trim();
        try {
            return switch (tipo) {
                case "integer", "smallint" -> new BigDecimal(texto.replace(",", ".")).intValueExact();
                case "bigint" -> new BigDecimal(texto.replace(",", ".")).longValueExact();
                case "numeric", "double precision", "real", "decimal" -> new BigDecimal(texto.replace(",", "."));
                case "boolean" -> switch (texto.toLowerCase(Locale.ROOT)) {
                    case "true", "sim", "1", "verdadeiro" -> Boolean.TRUE;
                    case "false", "nao", "não", "0", "falso" -> Boolean.FALSE;
                    default -> throw new IllegalArgumentException(texto);
                };
                case "date" -> LocalDate.parse(texto.length() >= 10 ? texto.substring(0, 10) : texto).toString();
                default -> texto;
            };
        } catch (RuntimeException exception) {
            throw new ToolException("Valor '" + texto + "' invalido para a coluna '" + coluna + "' (" + tipo + ").");
        }
    }

    private static String escaparLike(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String quote(String identificador) {
        return "\"" + identificador + "\"";
    }

    public record CompiledQuery(String sql, List<Object> parametros, SemanticCatalog.Fonte fonte, int limite) {
    }
}
