package com.nexo.app.modules.assistente.domain;

/** Como a IA externa foi conectada: pelo login (OAuth) ou por uma chave de acesso gerada pelo usuario. */
public enum TipoConexaoIa {
    OAUTH,
    CHAVE
}
