package com.nexo.app.modules.assistente.application.cadastro;

/** Tipo de um campo de cadastro: orienta a normalizacao do valor e o controle exibido no cartao. */
public enum TipoCampo {
    TEXTO,
    TEXTO_LONGO,
    INTEIRO,
    DECIMAL,
    MOEDA,
    DATA,
    DATA_HORA,
    HORA,
    BOOLEANO,
    OPCAO,
    REFERENCIA,
    LISTA,
    OBJETO,
    UUID,
    EMAIL,
    CPF,
    CNPJ,
    SENHA
}
