package com.nexo.app.modules.automacao.domain;

/**
 * Como a execução nasceu. Em {@code TESTE} os e-mails vão só para quem testa, as
 * esperas são puladas e as aprovações são aprovadas pela própria pessoa.
 */
public enum ModoExecucao {
    AUTOMATICA, MANUAL, TESTE, REENVIO
}
