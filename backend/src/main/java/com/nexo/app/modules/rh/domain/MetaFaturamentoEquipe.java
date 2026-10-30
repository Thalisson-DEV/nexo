package com.nexo.app.modules.rh.domain;

import com.nexo.app.modules.rh.CronogramaMetaFaturamento;
import com.nexo.app.modules.rh.JanelaMetaFaturamento;
import com.nexo.app.modules.rh.PeriodicidadeMetaFaturamento;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Meta de faturamento de uma equipe: quanto ela deve produzir, em reais, por janela
 * de apuração.
 *
 * <p>A janela vem da {@link PeriodicidadeMetaFaturamento}: o ciclo comercial (26 a 25)
 * para as equipes do processo comercial, ou o mês civil. A vigência é contada em
 * janelas inteiras — começa no primeiro dia de uma janela e, quando tem fim, termina
 * no último dia de outra —, então uma meta nunca cobre meio ciclo.</p>
 *
 * <p>Invariantes: a equipe é imutável após o cadastro; o valor é positivo; a vigência
 * é coerente e alinhada às janelas.</p>
 */
@Entity
@Table(name = "metas_faturamento_equipe")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetaFaturamentoEquipe extends BaseEntity {

    static final BigDecimal VALOR_MAXIMO = new BigDecimal("9999999999.99");

    @NotNull
    @Column(name = "id_equipe", nullable = false)
    private UUID idEquipe;

    @NotNull
    @Column(name = "valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "periodicidade", nullable = false, length = 20)
    private PeriodicidadeMetaFaturamento periodicidade;

    @NotNull
    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "descricao", length = 200)
    private String descricao;

    public MetaFaturamentoEquipe(UUID idEquipe, BigDecimal valor, PeriodicidadeMetaFaturamento periodicidade,
                                 LocalDate vigenciaInicio, LocalDate vigenciaFim, String descricao) {
        this.idEquipe = Objects.requireNonNull(idEquipe, "ID da equipe é obrigatório");
        aplicarDados(valor, periodicidade, vigenciaInicio, vigenciaFim, descricao);
    }

    /** Altera valor, periodicidade, vigência e descrição. A equipe é imutável. */
    public void atualizar(BigDecimal valor, PeriodicidadeMetaFaturamento periodicidade,
                          LocalDate vigenciaInicio, LocalDate vigenciaFim, String descricao) {
        aplicarDados(valor, periodicidade, vigenciaInicio, vigenciaFim, descricao);
    }

    /**
     * Encerra a meta no dia informado, que precisa ser o fim de uma janela. Usado
     * quando uma nova meta a substitui a partir da janela seguinte.
     */
    public void encerrarEm(LocalDate ultimoDia) {
        if (ultimoDia.isBefore(vigenciaInicio)) {
            throw new InvarianteVioladaException("A meta não pode ser encerrada antes do início da vigência.");
        }
        aplicarDados(valor, periodicidade, vigenciaInicio, ultimoDia, descricao);
    }

    public boolean estaVigenteEm(LocalDate dia) {
        return isAtivo()
                && !dia.isBefore(vigenciaInicio)
                && (vigenciaFim == null || !dia.isAfter(vigenciaFim));
    }

    /** Janela de apuração que contém o dia; vazio quando a meta não está vigente nele. */
    public Optional<JanelaMetaFaturamento> janelaEm(LocalDate dia) {
        return estaVigenteEm(dia) ? Optional.of(periodicidade.janelaEm(dia)) : Optional.empty();
    }

    /** Meta da janela vigente dividida pelos dias úteis dela; vazio fora da vigência. */
    public Optional<BigDecimal> metaDiariaEm(LocalDate dia) {
        return janelaEm(dia)
                .filter(janela -> janela.diasUteis() > 0)
                .map(janela -> valor.divide(BigDecimal.valueOf(janela.diasUteis()), 2, RoundingMode.HALF_UP));
    }

    /** Duas metas ativas da mesma equipe não podem cobrir o mesmo dia. */
    public boolean conflitaCom(MetaFaturamentoEquipe outra) {
        return idEquipe.equals(outra.idEquipe)
                && !(outra.vigenciaFim != null && vigenciaInicio.isAfter(outra.vigenciaFim))
                && !(vigenciaFim != null && vigenciaFim.isBefore(outra.vigenciaInicio));
    }

    public CronogramaMetaFaturamento.Vigencia comoVigencia() {
        return new CronogramaMetaFaturamento.Vigencia(valor, periodicidade, vigenciaInicio, vigenciaFim);
    }

    private void aplicarDados(BigDecimal valor, PeriodicidadeMetaFaturamento periodicidade,
                              LocalDate vigenciaInicio, LocalDate vigenciaFim, String descricao) {
        if (valor == null || valor.signum() <= 0) {
            throw new InvarianteVioladaException("O valor da meta de faturamento deve ser maior que zero.");
        }
        if (valor.compareTo(VALOR_MAXIMO) > 0) {
            throw new InvarianteVioladaException("O valor da meta de faturamento ultrapassa o limite permitido.");
        }
        if (periodicidade == null) {
            throw new InvarianteVioladaException("A periodicidade da meta é obrigatória.");
        }
        if (vigenciaInicio == null) {
            throw new InvarianteVioladaException("O início da vigência da meta é obrigatório.");
        }
        if (!periodicidade.iniciaJanela(vigenciaInicio)) {
            throw new InvarianteVioladaException(periodicidade == PeriodicidadeMetaFaturamento.CICLO_COMERCIAL
                    ? "A vigência de uma meta por ciclo deve começar no dia "
                            + PeriodicidadeMetaFaturamento.DIA_INICIO_CICLO + ", início do ciclo."
                    : "A vigência de uma meta mensal deve começar no dia 1º.");
        }
        if (vigenciaFim != null) {
            if (vigenciaFim.isBefore(vigenciaInicio)) {
                throw new InvarianteVioladaException("O fim da vigência não pode ser anterior ao início.");
            }
            if (!periodicidade.encerraJanela(vigenciaFim)) {
                throw new InvarianteVioladaException(periodicidade == PeriodicidadeMetaFaturamento.CICLO_COMERCIAL
                        ? "A vigência de uma meta por ciclo deve terminar no dia "
                                + (PeriodicidadeMetaFaturamento.DIA_INICIO_CICLO - 1) + ", fim do ciclo."
                        : "A vigência de uma meta mensal deve terminar no último dia do mês.");
            }
        }

        this.valor = valor.setScale(2, RoundingMode.HALF_UP);
        this.periodicidade = periodicidade;
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
        this.descricao = descricao == null || descricao.isBlank() ? null : descricao.trim();
    }
}
