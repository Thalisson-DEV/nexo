package com.nexo.app.modules.rh;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Como as janelas de apuração de uma meta de faturamento se repetem.
 *
 * <ul>
 *   <li>{@link #CICLO_COMERCIAL}: do dia {@value #DIA_INICIO_CICLO} de um mês ao dia
 *       {@code DIA_INICIO_CICLO - 1} do mês seguinte. É o ciclo das equipes do processo
 *       comercial; a competência é o mês em que o ciclo termina (26/09 a 25/10 é o
 *       ciclo de outubro).</li>
 *   <li>{@link #MENSAL}: o mês civil, do dia 1º ao último dia.</li>
 * </ul>
 *
 * <p>As janelas são fixas no calendário — não dependem da data de cadastro —, então
 * duas equipes com a mesma periodicidade sempre fecham o período no mesmo dia.</p>
 */
public enum PeriodicidadeMetaFaturamento {

    CICLO_COMERCIAL,
    MENSAL;

    public static final int DIA_INICIO_CICLO = 26;

    /** Janela que contém a data informada. */
    public JanelaMetaFaturamento janelaEm(LocalDate dia) {
        return janelaDaCompetencia(competenciaDe(dia));
    }

    /** Competência (mês de referência) da janela que contém a data. */
    public YearMonth competenciaDe(LocalDate dia) {
        YearMonth mes = YearMonth.from(dia);
        return this == CICLO_COMERCIAL && dia.getDayOfMonth() >= DIA_INICIO_CICLO ? mes.plusMonths(1) : mes;
    }

    /** Janela de uma competência: o ciclo que termina nela ou o próprio mês civil. */
    public JanelaMetaFaturamento janelaDaCompetencia(YearMonth competencia) {
        if (this == CICLO_COMERCIAL) {
            return new JanelaMetaFaturamento(
                    competencia.minusMonths(1).atDay(DIA_INICIO_CICLO),
                    competencia.atDay(DIA_INICIO_CICLO - 1),
                    competencia);
        }
        return new JanelaMetaFaturamento(competencia.atDay(1), competencia.atEndOfMonth(), competencia);
    }

    public boolean iniciaJanela(LocalDate dia) {
        return janelaEm(dia).inicio().equals(dia);
    }

    public boolean encerraJanela(LocalDate dia) {
        return janelaEm(dia).fim().equals(dia);
    }
}
