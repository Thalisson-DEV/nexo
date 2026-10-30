package com.nexo.app.modules.execucao.domain;

/**
 * De onde veio o material aplicado ou retirado.
 */
public enum OrigemMaterial {
    /** Saldo da equipe: aplicação debita e retirada entra como sucata. */
    EQUIPE,
    /** Material da concessionária (Concessionária), só na ocorrência: fica como registro e não toca o saldo. */
    CONCESSIONARIA
}
