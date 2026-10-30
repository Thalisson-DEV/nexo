package com.nexo.app.modules.assistente.domain;

/** Credenciais de uma conexao de IA. So o hash e guardado. */
public enum TipoCredencialIa {
    /** Token de acesso do OAuth: vida curta. */
    ACESSO,
    /** Token de renovacao do OAuth: uso unico, trocado a cada renovacao. */
    RENOVACAO,
    /** Chave de acesso gerada pelo usuario na tela (clientes que aceitam cabecalho). */
    CHAVE
}
