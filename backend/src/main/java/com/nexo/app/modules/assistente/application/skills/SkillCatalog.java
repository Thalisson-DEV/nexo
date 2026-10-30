package com.nexo.app.modules.assistente.application.skills;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Catalogo de competencias (skills) do Copiloto: roteiros de analise em markdown carregados sob demanda.
 * O prompt de sistema lista apenas nome e descricao; o conteudo completo entra no contexto quando o agente
 * chama {@code usar_competencia}, mantendo o prompt enxuto (mesmo principio das skills do Onyx/Claude).
 * Arquivos em {@code classpath:copiloto/skills/*.md} com front matter (nome, titulo, descricao, autoridades).
 */
@Component
public class SkillCatalog {

    private final Map<String, Skill> skills;

    public SkillCatalog() {
        this.skills = carregar();
    }

    public List<Skill> disponiveis(SemanticQueryContext contexto) {
        return skills.values().stream().filter(skill -> skill.permitida(contexto)).toList();
    }

    public Optional<Skill> buscar(String nome) {
        if (nome == null) return Optional.empty();
        return Optional.ofNullable(skills.get(nome.trim().toLowerCase(Locale.ROOT)));
    }

    private static Map<String, Skill> carregar() {
        Map<String, Skill> carregadas = new TreeMap<>();
        try {
            Resource[] recursos = new PathMatchingResourcePatternResolver().getResources("classpath*:copiloto/skills/*.md");
            for (Resource recurso : recursos) {
                String conteudo = new String(recurso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Skill skill = parse(conteudo, Objects.toString(recurso.getFilename(), "skill.md"));
                carregadas.put(skill.nome(), skill);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Falha ao carregar competencias do Copiloto", exception);
        }
        return Collections.unmodifiableMap(carregadas);
    }

    static Skill parse(String conteudo, String arquivo) {
        String texto = conteudo.replace("\r", "");
        if (texto.startsWith("\uFEFF")) texto = texto.substring(1);
        Map<String, String> meta = new HashMap<>();
        String corpo = texto;
        if (texto.startsWith("---\n")) {
            int fim = texto.indexOf("\n---", 4);
            if (fim > 0) {
                for (String linha : texto.substring(4, fim).split("\n")) {
                    int separador = linha.indexOf(':');
                    if (separador > 0) meta.put(linha.substring(0, separador).trim(), linha.substring(separador + 1).trim());
                }
                corpo = texto.substring(fim + 4).strip();
            }
        }
        String nome = meta.getOrDefault("nome", arquivo.replaceFirst("\\.md$", "")).toLowerCase(Locale.ROOT);
        Set<String> autoridades = new LinkedHashSet<>();
        String bruto = meta.getOrDefault("autoridades", "").replace("[", "").replace("]", "");
        for (String autoridade : bruto.split(",")) if (!autoridade.isBlank()) autoridades.add(autoridade.trim());
        return new Skill(nome, meta.getOrDefault("titulo", nome), meta.getOrDefault("descricao", ""), Set.copyOf(autoridades), corpo);
    }

    public record Skill(String nome, String titulo, String descricao, Set<String> autoridades, String conteudo) {
        public boolean permitida(SemanticQueryContext contexto) {
            return autoridades.isEmpty() || autoridades.stream().anyMatch(contexto::possuiAutoridade);
        }
    }
}
