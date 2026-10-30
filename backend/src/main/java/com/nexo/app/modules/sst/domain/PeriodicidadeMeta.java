package com.nexo.app.modules.sst.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Define a duração da meta de forma dinâmica: em vez de cadastrar cada período
 * manualmente, o usuário escolhe a periodicidade e a data de início da vigência.
 * As janelas de apuração passam a se repetir sozinhas a partir dessa âncora
 * (ex.: MENSAL a partir de 01/03 gera 01/03–31/03, 01/04–30/04, e assim por diante),
 * sem gaps nem sobreposição, até o fim da vigência (ou indefinidamente).
 *
 * <p>{@link #PERSONALIZADO} é a saída para campanhas pontuais: a janela é uma só e
 * corresponde exatamente ao intervalo de vigência informado.</p>
 */
public enum PeriodicidadeMeta {

    SEMANAL(ChronoUnit.DAYS, 7),
    QUINZENAL(ChronoUnit.DAYS, 15),
    MENSAL(ChronoUnit.MONTHS, 1),
    BIMESTRAL(ChronoUnit.MONTHS, 2),
    TRIMESTRAL(ChronoUnit.MONTHS, 3),
    SEMESTRAL(ChronoUnit.MONTHS, 6),
    ANUAL(ChronoUnit.MONTHS, 12),
    PERSONALIZADO(null, 0);

    private final ChronoUnit unidade;
    private final int passo;

    PeriodicidadeMeta(ChronoUnit unidade, int passo) {
        this.unidade = unidade;
        this.passo = passo;
    }

    /** Indica se a periodicidade se repete em ciclos (falso apenas para PERSONALIZADO). */
    public boolean isRecorrente() {
        return unidade != null;
    }

    /**
     * Calcula o ciclo que contém a data de referência, contado a partir da âncora
     * (data de início da vigência). Só se aplica a periodicidades recorrentes.
     */
    public JanelaMeta janela(LocalDate ancora, LocalDate referencia) {
        if (!isRecorrente()) {
            throw new IllegalStateException("A periodicidade PERSONALIZADO usa o próprio intervalo de vigência.");
        }
        long ciclo = Math.max(0, unidade.between(ancora, referencia) / passo);
        LocalDate inicio = ancora.plus(ciclo * passo, unidade);
        LocalDate fim = ancora.plus((ciclo + 1) * passo, unidade).minusDays(1);
        return new JanelaMeta(inicio, fim, (int) ciclo + 1);
    }
}
