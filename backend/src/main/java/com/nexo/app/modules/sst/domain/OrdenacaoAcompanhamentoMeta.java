package com.nexo.app.modules.sst.domain;

/**
 * Critérios de ordenação do acompanhamento de metas de inspeção.
 *
 * <p>O acompanhamento devolve a lista inteira (não é paginado), por isso a ordenação
 * não vem de um {@code Pageable}: o critério é escolhido por nome e a direção por
 * {@code ASC}/{@code DESC}. O padrão é {@link #PERCENTUAL} crescente, que coloca no
 * topo quem está mais distante da meta — a leitura que interessa à gestão.</p>
 */
public enum OrdenacaoAcompanhamentoMeta {

    /** Percentual de atingimento da meta. */
    PERCENTUAL,
    /** Inspeções lançadas na janela. */
    REALIZADO,
    /** Quantidade exigida pela meta. */
    META,
    /** Quantas faltam para atingir a meta. */
    SALDO,
    /** Dias que ainda restam na janela de apuração. */
    DIAS_RESTANTES,
    /** Nome do colaborador. */
    COLABORADOR,
    /** Nome da função exercida. */
    FUNCAO,
    /** Nome da base operacional. */
    BASE_OPERACIONAL,
    /** Nome do processo de negócio. */
    PROCESSO,
    /** Nome do centro de custo. */
    CENTRO_CUSTO,
    /** Situação diante da meta (ATINGIDA, EM_ANDAMENTO, NAO_ATINGIDA). */
    STATUS
}
