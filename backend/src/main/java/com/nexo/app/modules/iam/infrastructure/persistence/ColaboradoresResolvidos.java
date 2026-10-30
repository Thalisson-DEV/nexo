package com.nexo.app.modules.iam.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

/**
 * IDs de colaboradores que o RH já resolveu para um filtro de usuários.
 *
 * <p>A {@link UsuarioSpecification} não pode procurar por nome de colaborador sozinha:
 * o nome está cifrado em outro módulo, e {@code usuarios.id_colaborador} é só um
 * identificador solto — não há {@code join} a fazer. Quem monta a consulta pergunta
 * antes ao RH quais colaboradores casam com o texto digitado e traz o resultado aqui.</p>
 *
 * <p>A distinção entre "não filtrei" e "filtrei e não achei ninguém" é a razão de este
 * tipo existir: em {@link #porNome()}, {@code null} significa que o usuário não pediu
 * filtro por nome, enquanto uma lista vazia significa que ninguém corresponde — e aí a
 * listagem tem de vir vazia, não completa.</p>
 *
 * @param porNome        ids que casam com {@code nomeColaborador}; {@code null} quando o filtro não foi usado
 * @param porBuscaGlobal ids que casam com {@code buscaGlobal}; vazio apenas soma nada ao OR da busca rápida
 */
public record ColaboradoresResolvidos(List<UUID> porNome, List<UUID> porBuscaGlobal) {

    public ColaboradoresResolvidos {
        porBuscaGlobal = porBuscaGlobal == null ? List.of() : List.copyOf(porBuscaGlobal);
        porNome = porNome == null ? null : List.copyOf(porNome);
    }

    /** Nenhum filtro dependente do RH foi informado. */
    public static ColaboradoresResolvidos nenhum() {
        return new ColaboradoresResolvidos(null, List.of());
    }

    public boolean filtrouPorNome() {
        return porNome != null;
    }
}
