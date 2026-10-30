package com.nexo.app.modules.financeiro.domain;

/**
 * Enumeração que carrega o próprio texto de exibição.
 *
 * <p>Permite ao gerador de schema publicar as opções de um campo apoiado numa
 * enumeração fechada já com o rótulo pronto, em vez de expor a constante crua e
 * deixar a tradução a cargo do frontend — que então teria de ser atualizado
 * sempre que uma constante fosse acrescentada.</p>
 */
public interface Rotulavel {

    /** Texto exibido ao usuário. */
    String getRotulo();
}
