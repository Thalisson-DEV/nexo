package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Grupos e perguntas de um modelo de checklist, no mesmo desenho dos formulários de inspeção do SST. Cada pergunta
 * guarda um identificador próprio, que liga a resposta à pergunta mesmo se o texto for corrigido. Na ficha As Built, o
 * modelo também lista as fotos que o book pede em cada ponto.
 */
public record EstruturaChecklist(List<Grupo> grupos, List<ItemEvidencia> evidencias) {

    /** Modelo gravado antes do book não tem a lista de fotos. */
    public EstruturaChecklist {
        evidencias = evidencias == null ? List.of() : evidencias;
    }

    public EstruturaChecklist(List<Grupo> grupos) {
        this(grupos, List.of());
    }

    public enum TipoPergunta {
        ESCOLHA_UNICA,
        MULTIPLA_ESCOLHA,
        TEXTO,
        NUMERO,
        DATA;

        boolean temOpcoes() {
            return this == ESCOLHA_UNICA || this == MULTIPLA_ESCOLHA;
        }
    }

    public record Grupo(String titulo, List<Pergunta> perguntas) {
    }

    public record Pergunta(String id, String enunciado, TipoPergunta tipo, boolean obrigatoria, boolean aceitaNaoSeAplica,
                           List<String> opcoes) {
    }

    public record ItemEvidencia(String id, String titulo, boolean obrigatoria) {
    }

    void validar() {
        validarEvidencias();
        if (grupos == null || grupos.isEmpty()) {
            throw new InvarianteVioladaException("Inclua ao menos um grupo no modelo.");
        }
        Set<String> ids = new HashSet<>();
        for (Grupo grupo : grupos) {
            if (grupo.titulo() == null || grupo.titulo().isBlank()) {
                throw new InvarianteVioladaException("Informe o título de todos os grupos.");
            }
            if (grupo.perguntas() == null || grupo.perguntas().isEmpty()) {
                throw new InvarianteVioladaException("O grupo \"" + grupo.titulo() + "\" não tem perguntas.");
            }
            grupo.perguntas().forEach(pergunta -> validar(grupo, pergunta, ids));
        }
    }

    private void validarEvidencias() {
        Set<String> ids = new HashSet<>();
        for (ItemEvidencia item : evidencias) {
            if (item.id() == null || item.id().isBlank() || item.id().length() > 100 || !ids.add(item.id())) {
                throw new InvarianteVioladaException("Há item de evidência sem identificador único.");
            }
            if (item.titulo() == null || item.titulo().isBlank()) {
                throw new InvarianteVioladaException("Informe o título de todos os itens de evidência.");
            }
            if (item.titulo().length() > 150) {
                throw new InvarianteVioladaException("O item de evidência \"" + item.titulo() + "\" passa de 150 caracteres.");
            }
        }
    }

    private static void validar(Grupo grupo, Pergunta pergunta, Set<String> ids) {
        if (pergunta.id() == null || pergunta.id().isBlank() || !ids.add(pergunta.id())) {
            throw new InvarianteVioladaException("Há pergunta sem identificador único no grupo \"" + grupo.titulo() + "\".");
        }
        if (pergunta.enunciado() == null || pergunta.enunciado().isBlank()) {
            throw new InvarianteVioladaException("Há pergunta sem enunciado no grupo \"" + grupo.titulo() + "\".");
        }
        if (pergunta.tipo() == null) {
            throw new InvarianteVioladaException("Informe o tipo de resposta de \"" + pergunta.enunciado() + "\".");
        }
        if (!pergunta.tipo().temOpcoes()) {
            return;
        }
        List<String> opcoes = pergunta.opcoes() == null ? List.of() : pergunta.opcoes();
        if (opcoes.stream().anyMatch(opcao -> opcao == null || opcao.isBlank())) {
            throw new InvarianteVioladaException("Há opção em branco em \"" + pergunta.enunciado() + "\".");
        }
        if (opcoes.size() < 2 || new HashSet<>(opcoes).size() != opcoes.size()) {
            throw new InvarianteVioladaException("\"" + pergunta.enunciado() + "\" precisa de ao menos duas opções diferentes.");
        }
    }
}
