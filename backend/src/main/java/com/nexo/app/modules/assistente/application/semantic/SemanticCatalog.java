package com.nexo.app.modules.assistente.application.semantic;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Camada semantica do Nexo para o agente: significado de negocio, permissoes, enums, relacionamentos e
 * glossario das views do schema {@code copiloto}. O conteudo e descoberto sob demanda pelas ferramentas
 * de catalogo — nunca e enviado inteiro ao prompt. Tipos de coluna vem do proprio banco.
 */
@Component
public class SemanticCatalog {

    public static final String AUTENTICADO = "@autenticado";
    private static final Logger LOGGER = LoggerFactory.getLogger(SemanticCatalog.class);
    private static final String RESOURCE = "copiloto/semantic-catalog.yaml";
    private static final Duration CACHE_METADADOS = Duration.ofMinutes(10);

    private final JdbcTemplate jdbcTemplate;
    private final String versao;
    private final List<Dominio> dominios;
    private final Map<String, Fonte> fontes;
    private final List<TermoGlossario> glossario;

    private volatile Map<String, List<ColunaBanco>> colunasBanco = Map.of();
    private volatile Instant metadadosCarregadosEm = Instant.EPOCH;
    private volatile boolean schemaDisponivel;

    public SemanticCatalog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        Map<String, Object> raiz = carregarYaml();
        this.versao = String.valueOf(raiz.getOrDefault("versao", "1.0.0"));
        this.dominios = lista(raiz.get("dominios")).stream().map(item -> {
            Map<String, Object> mapa = mapa(item);
            return new Dominio(texto(mapa.get("id")), texto(mapa.get("nome")), texto(mapa.get("descricao")));
        }).toList();
        Map<String, Fonte> indexadas = new LinkedHashMap<>();
        mapa(raiz.get("fontes")).forEach((id, valor) -> indexadas.put(id, fonte(id, mapa(valor))));
        this.fontes = Collections.unmodifiableMap(indexadas);
        this.glossario = lista(raiz.get("glossario")).stream().map(item -> {
            Map<String, Object> mapa = mapa(item);
            return new TermoGlossario(texto(mapa.get("termo")), textos(mapa.get("sinonimos")),
                    texto(mapa.get("definicao")), textos(mapa.get("fontes")));
        }).toList();
    }

    public String versao() {
        return versao;
    }

    public List<Dominio> dominios() {
        return dominios;
    }

    public Optional<Fonte> fonte(String id) {
        if (id == null) return Optional.empty();
        String normalizado = id.trim().toLowerCase(Locale.ROOT);
        if (normalizado.startsWith("copiloto.")) normalizado = normalizado.substring("copiloto.".length());
        return Optional.ofNullable(fontes.get(normalizado));
    }

    public Collection<Fonte> todas() {
        return fontes.values();
    }

    public boolean autorizado(Fonte fonte, SemanticQueryContext contexto) {
        return fonte.autoridades().stream()
                .anyMatch(autoridade -> AUTENTICADO.equals(autoridade) || contexto.possuiAutoridade(autoridade));
    }

    public List<Fonte> autorizadas(SemanticQueryContext contexto) {
        return fontes.values().stream().filter(fonte -> autorizado(fonte, contexto)).toList();
    }

    /** Chamados: quem gerencia ou tem painel ve todos; os demais veem somente os proprios. */
    public boolean veTodosChamados(SemanticQueryContext contexto) {
        return contexto.possuiAutoridade("chamado:manage") || contexto.possuiAutoridade("chamado:dashboard:read");
    }

    public List<TermoGlossario> buscarGlossario(String termo) {
        String alvo = normalizar(termo);
        if (alvo.isBlank()) return glossario;
        return glossario.stream()
                .filter(item -> normalizar(item.termo()).contains(alvo)
                        || item.sinonimos().stream().anyMatch(sinonimo -> normalizar(sinonimo).contains(alvo)
                                || alvo.contains(normalizar(sinonimo)))
                        || normalizar(item.definicao()).contains(alvo))
                .toList();
    }

    /** Fontes cujo nome, descricao, palavras-chave ou colunas mencionam o termo. */
    public List<Fonte> buscarFontes(String termo, SemanticQueryContext contexto) {
        String alvo = normalizar(termo);
        return autorizadas(contexto).stream()
                .filter(fonte -> alvo.isBlank()
                        || normalizar(fonte.nome() + " " + fonte.descricao() + " " + fonte.id()).contains(alvo)
                        || fonte.palavrasChave().stream().anyMatch(chave -> normalizar(chave).contains(alvo)
                                || alvo.contains(normalizar(chave)))
                        || fonte.colunas().keySet().stream().anyMatch(coluna -> coluna.contains(alvo)))
                .toList();
    }

    /** O schema semantico existe neste banco (PostgreSQL com a migration V152 aplicada)? */
    public boolean schemaDisponivel() {
        atualizarMetadadosSeNecessario();
        return schemaDisponivel;
    }

    public List<ColunaBanco> colunasDoBanco(String fonteId) {
        atualizarMetadadosSeNecessario();
        return colunasBanco.getOrDefault(fonteId, List.of());
    }

    private void atualizarMetadadosSeNecessario() {
        if (Duration.between(metadadosCarregadosEm, Instant.now()).compareTo(CACHE_METADADOS) < 0) return;
        synchronized (this) {
            if (Duration.between(metadadosCarregadosEm, Instant.now()).compareTo(CACHE_METADADOS) < 0) return;
            try {
                List<Map<String, Object>> linhas = jdbcTemplate.queryForList("""
                        select table_name, column_name, data_type
                        from information_schema.columns
                        where table_schema = 'copiloto'
                        order by table_name, ordinal_position
                        """);
                Map<String, List<ColunaBanco>> porTabela = new LinkedHashMap<>();
                for (Map<String, Object> linha : linhas) {
                    String tabela = String.valueOf(linha.get("table_name")).toLowerCase(Locale.ROOT);
                    porTabela.computeIfAbsent(tabela, ignored -> new ArrayList<>()).add(new ColunaBanco(
                            String.valueOf(linha.get("column_name")).toLowerCase(Locale.ROOT),
                            String.valueOf(linha.get("data_type")).toLowerCase(Locale.ROOT)));
                }
                colunasBanco = Collections.unmodifiableMap(porTabela);
                schemaDisponivel = !porTabela.isEmpty();
            } catch (RuntimeException exception) {
                LOGGER.debug("Metadados do schema copiloto indisponiveis: {}", exception.getClass().getSimpleName());
                colunasBanco = Map.of();
                schemaDisponivel = false;
            }
            metadadosCarregadosEm = Instant.now();
        }
    }

    // ----------------------------------------------------------------------------------- YAML

    private static Map<String, Object> carregarYaml() {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            Object carregado = new Yaml().load(input);
            return mapa(carregado);
        } catch (IOException exception) {
            throw new IllegalStateException("Catalogo semantico do Copiloto ausente: " + RESOURCE, exception);
        }
    }

    private static Fonte fonte(String id, Map<String, Object> mapa) {
        Map<String, ColunaSemantica> colunas = new LinkedHashMap<>();
        mapa(mapa.get("colunas")).forEach((nome, valor) -> {
            if (valor instanceof Map<?, ?> detalhe) {
                colunas.put(nome, new ColunaSemantica(texto(detalhe.get("descricao")), textos(detalhe.get("valores"))));
            } else {
                colunas.put(nome, new ColunaSemantica(texto(valor), List.of()));
            }
        });
        List<String> autoridades = textos(mapa.get("autoridades"));
        if (autoridades.isEmpty()) throw new IllegalStateException("Fonte semantica sem autoridade: " + id);
        return new Fonte(id, texto(mapa.get("dominio")), texto(mapa.get("nome")), texto(mapa.get("descricao")),
                autoridades, Optional.ofNullable(texto(mapa.get("escopo"))).orElse("todos"),
                textos(mapa.get("palavras_chave")), texto(mapa.get("coluna_data")), colunas,
                textos(mapa.get("relacionamentos")), textos(mapa.get("exemplos")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            Map<String, Object> copia = new LinkedHashMap<>();
            ((Map<Object, Object>) mapa).forEach((chave, item) -> copia.put(String.valueOf(chave), item));
            return copia;
        }
        return new LinkedHashMap<>();
    }

    private static List<Object> lista(Object valor) {
        return valor instanceof List<?> lista ? new ArrayList<>(lista) : List.of();
    }

    private static String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor).trim();
    }

    private static List<String> textos(Object valor) {
        if (valor == null) return List.of();
        if (valor instanceof Collection<?> colecao) {
            return colecao.stream().filter(Objects::nonNull).map(String::valueOf).map(String::trim).toList();
        }
        return List.of(String.valueOf(valor).trim());
    }

    public static String normalizar(String valor) {
        if (valor == null) return "";
        String semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    // ----------------------------------------------------------------------------------- tipos

    public record Dominio(String id, String nome, String descricao) {
    }

    public record Fonte(
            String id,
            String dominio,
            String nome,
            String descricao,
            List<String> autoridades,
            String escopo,
            List<String> palavrasChave,
            String colunaData,
            Map<String, ColunaSemantica> colunas,
            List<String> relacionamentos,
            List<String> exemplos) {

        public String nomeQualificado() {
            return "copiloto." + id;
        }
    }

    public record ColunaSemantica(String descricao, List<String> valores) {
    }

    public record ColunaBanco(String nome, String tipo) {
    }

    public record TermoGlossario(String termo, List<String> sinonimos, String definicao, List<String> fontes) {
    }

    /** Resumo compacto (usado por listar_fontes_dados). */
    public static String resumoAutoridades(Fonte fonte) {
        return fonte.autoridades().stream().collect(Collectors.joining(", "));
    }
}
