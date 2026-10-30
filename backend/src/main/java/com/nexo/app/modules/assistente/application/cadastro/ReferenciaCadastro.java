package com.nexo.app.modules.assistente.application.cadastro;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Cadastro que outros cadastros referenciam (funcao, base, equipe, material...). O modulo dono declara uma
 * consulta somente leitura que devolve as colunas {@code id}, {@code rotulo} e {@code detalhe}; o Copiloto a
 * usa para achar o registro pelo nome que o usuario falou, sem que ninguem precise saber ids.
 *
 * @param tipo     chave estavel ("funcao", "base_operacional")
 * @param rotulo   nome exibido ("Função")
 * @param sql      SELECT id, rotulo, detalhe FROM ... (somente leitura, sem parametros); a busca compara o
 *                 termo com {@code rotulo} e {@code detalhe}
 * @param exibicao opcional: troca o rotulo encontrado pelo exibido (ex.: nome descriptografado pelo modulo,
 *                 quando a busca usa uma coluna normalizada)
 */
public record ReferenciaCadastro(String tipo, String rotulo, String sql, Function<Collection<UUID>, Map<UUID, String>> exibicao) {

    public ReferenciaCadastro(String tipo, String rotulo, String sql) {
        this(tipo, rotulo, sql, null);
    }

    public ReferenciaCadastro {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(rotulo, "rotulo");
        Objects.requireNonNull(sql, "sql");
        if (!tipo.matches("[a-z][a-z0-9_]{1,60}")) throw new IllegalArgumentException("Tipo de referencia invalido: " + tipo);
        String normalizado = sql.strip().toLowerCase();
        if (!normalizado.startsWith("select") || sql.contains(";")) {
            throw new IllegalArgumentException("A referencia " + tipo + " deve ser um unico SELECT");
        }
    }
}
