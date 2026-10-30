package com.nexo.app.modules.rh;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Metas de faturamento ativas de uma equipe ao longo do tempo, prontas para serem
 * comparadas com a produção em qualquer recorte.
 *
 * <p>A meta vale por janela (ciclo comercial ou mês civil). Para um recorte qualquer
 * — um dia, um mês civil que atravessa dois ciclos, um ano —, cada dia útil coberto
 * por uma vigência recebe o valor da janela dividido pelos dias úteis dela. Assim o
 * mês de outubro, por exemplo, soma a parte do ciclo de outubro (01 a 25) com a parte
 * do ciclo de novembro (26 a 31).</p>
 */
public record CronogramaMetaFaturamento(List<Vigencia> vigencias) {

    private static final int ESCALA = 2;
    private static final int ESCALA_RATEIO = 6;

    /** Uma meta ativa: valor por janela, periodicidade e vigência ({@code fim} nulo = indeterminada). */
    public record Vigencia(BigDecimal valor, PeriodicidadeMetaFaturamento periodicidade,
                           LocalDate inicio, LocalDate fim) {

        public Vigencia {
            Objects.requireNonNull(valor, "valor");
            Objects.requireNonNull(periodicidade, "periodicidade");
            Objects.requireNonNull(inicio, "inicio");
        }

        public boolean cobre(LocalDate dia) {
            return !dia.isBefore(inicio) && (fim == null || !dia.isAfter(fim));
        }

        public boolean tocaAlgumDiaEntre(LocalDate de, LocalDate ate) {
            return !inicio.isAfter(ate) && (fim == null || !fim.isBefore(de));
        }
    }

    public CronogramaMetaFaturamento {
        vigencias = vigencias == null ? List.of() : vigencias.stream()
                .sorted(Comparator.comparing(Vigencia::inicio))
                .toList();
    }

    public static CronogramaMetaFaturamento vazio() {
        return new CronogramaMetaFaturamento(List.of());
    }

    /** Meta única e sem prazo — útil quando a vigência não importa (testes, simulações). */
    public static CronogramaMetaFaturamento constante(BigDecimal valor, PeriodicidadeMetaFaturamento periodicidade) {
        return new CronogramaMetaFaturamento(List.of(new Vigencia(valor, periodicidade, LocalDate.MIN, null)));
    }

    public boolean isVazio() {
        return vigencias.isEmpty();
    }

    public Optional<Vigencia> vigenciaEm(LocalDate dia) {
        return vigencias.stream().filter(v -> v.cobre(dia)).findFirst();
    }

    /** Valor da meta na janela que contém o dia; {@code null} sem meta vigente nesse dia. */
    public BigDecimal valorDaJanelaEm(LocalDate dia) {
        return vigenciaEm(dia).map(Vigencia::valor).orElse(null);
    }

    public Optional<JanelaMetaFaturamento> janelaEm(LocalDate dia) {
        return vigenciaEm(dia).map(v -> v.periodicidade().janelaEm(dia));
    }

    public boolean possuiMetaEntre(LocalDate inicio, LocalDate fim) {
        return vigencias.stream().anyMatch(v -> v.tocaAlgumDiaEntre(inicio, fim));
    }

    /** Meta de um dia: o valor da janela dividido pelos dias úteis dela; {@code null} em dia não útil ou sem meta. */
    public BigDecimal metaDiaria(LocalDate dia) {
        BigDecimal rateio = rateioDiario(dia);
        return rateio == null ? null : rateio.setScale(ESCALA, RoundingMode.HALF_UP);
    }

    /**
     * Parte da meta que cabe em {@code [inicio, fim]}: a soma das metas diárias dos dias
     * úteis do intervalo. {@code null} quando nenhuma vigência toca o intervalo.
     */
    public BigDecimal metaProporcional(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            return isVazio() ? null : BigDecimal.ZERO.setScale(ESCALA);
        }
        if (!possuiMetaEntre(inicio, fim)) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            BigDecimal rateio = rateioDiario(dia);
            if (rateio != null) {
                total = total.add(rateio);
            }
        }
        return total.setScale(ESCALA, RoundingMode.HALF_UP);
    }

    private BigDecimal rateioDiario(LocalDate dia) {
        if (!JanelaMetaFaturamento.diaUtil(dia)) {
            return null;
        }
        return vigenciaEm(dia)
                .map(v -> {
                    int uteis = v.periodicidade().janelaEm(dia).diasUteis();
                    return uteis == 0 ? null
                            : v.valor().divide(BigDecimal.valueOf(uteis), ESCALA_RATEIO, RoundingMode.HALF_UP);
                })
                .orElse(null);
    }
}
