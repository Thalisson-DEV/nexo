package com.nexo.app.modules.sst.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Janela de apuração vigente de uma meta: o intervalo fechado de datas dentro do
 * qual a quantidade exigida precisa ser cumprida, e o número do ciclo desde o
 * início da vigência (1 = primeiro ciclo).
 */
public record JanelaMeta(LocalDate inicio, LocalDate fim, int ciclo) {

    /** Dias que ainda restam para cumprir a meta (0 quando a janela já se encerrou). */
    public long diasRestantes(LocalDate referencia) {
        return Math.max(0, ChronoUnit.DAYS.between(referencia, fim));
    }

    /** Total de dias da janela (intervalo fechado). */
    public long dias() {
        return ChronoUnit.DAYS.between(inicio, fim) + 1;
    }

    public boolean encerradaEm(LocalDate referencia) {
        return referencia.isAfter(fim);
    }

    public boolean contem(LocalDate data) {
        return !data.isBefore(inicio) && !data.isAfter(fim);
    }
}
