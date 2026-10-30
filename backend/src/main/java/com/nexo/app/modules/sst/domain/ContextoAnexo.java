package com.nexo.app.modules.sst.domain;

/**
 * Indica a que parte do {@link DesvioSeguranca} uma evidência (anexo) se refere.
 */
public enum ContextoAnexo {
    /** Evidência da não-conformidade em si. */
    DESVIO,
    /** Evidência da ação corretiva adotada. */
    ACAO_CORRETIVA
}
