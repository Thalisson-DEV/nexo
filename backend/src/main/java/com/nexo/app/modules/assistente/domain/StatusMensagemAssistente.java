package com.nexo.app.modules.assistente.domain;

public enum StatusMensagemAssistente {
    PROCESSANDO,
    CONCLUIDA,
    INDISPONIVEL,
    DADOS_INSUFICIENTES,
    /** O usuario interrompeu a geracao; o conteudo parcial fica preservado. */
    CANCELADA,
    /** A execucao terminou por falha do provedor, de ferramenta ou reinicio do servidor. */
    FALHOU;

    public boolean isFinal() {
        return this != PROCESSANDO;
    }
}
