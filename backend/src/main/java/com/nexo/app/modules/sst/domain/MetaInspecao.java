package com.nexo.app.modules.sst.domain;

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
 * Meta de inspeções de segurança: quantas inspeções um alvo (uma função inteira ou
 * um colaborador específico) deve realizar por janela de apuração.
 *
 * <p>A duração da meta é definida por dois campos apenas — a {@link PeriodicidadeMeta}
 * e a data de início da vigência —, e as janelas se repetem automaticamente a partir
 * daí. A vigência pode ter fim (campanha) ou ser indeterminada.</p>
 *
 * <p>Invariantes: o alvo é exatamente um (função <b>ou</b> colaborador) e é imutável
 * após a criação; a quantidade é positiva; a vigência é coerente; e
 * {@link PeriodicidadeMeta#PERSONALIZADO} exige data de fim, pois a janela é a
 * própria vigência.</p>
 */
@Entity
@Table(name = "metas_inspecao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetaInspecao extends BaseEntity {

    private static final int QUANTIDADE_MAXIMA = 999;
    private static final int ESCALA_RATEIO = 6;

    @NotNull
    @Column(name = "id_tipo_inspecao", nullable = false)
    private UUID idTipoInspecao;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "escopo", nullable = false, length = 20)
    private EscopoMeta escopo;

    @Column(name = "id_funcao")
    private UUID idFuncao;

    @Column(name = "id_colaborador")
    private UUID idColaborador;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "periodicidade", nullable = false, length = 20)
    private PeriodicidadeMeta periodicidade;

    @NotNull
    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "descricao", length = 200)
    private String descricao;

    public MetaInspecao(UUID idTipoInspecao, UUID idFuncao, UUID idColaborador, int quantidade, PeriodicidadeMeta periodicidade,
                        LocalDate vigenciaInicio, LocalDate vigenciaFim, String descricao) {
        this.idTipoInspecao = Objects.requireNonNull(idTipoInspecao, "ID do tipo de inspeção é obrigatório");
        this.escopo = definirEscopo(idFuncao, idColaborador);
        this.idFuncao = idFuncao;
        this.idColaborador = idColaborador;
        aplicarDados(quantidade, periodicidade, vigenciaInicio, vigenciaFim, descricao);
    }

    /**
     * Altera a quantidade exigida e a duração da meta. O alvo (função/colaborador)
     * é imutável: apontar a meta para outro alvo significa cadastrar outra meta.
     */
    public void atualizar(int quantidade, PeriodicidadeMeta periodicidade,
                          LocalDate vigenciaInicio, LocalDate vigenciaFim, String descricao) {
        aplicarDados(quantidade, periodicidade, vigenciaInicio, vigenciaFim, descricao);
    }

    /** Indica se a meta está ativa e dentro do intervalo de vigência na data informada. */
    public boolean estaVigenteEm(LocalDate referencia) {
        return isAtivo()
                && !referencia.isBefore(vigenciaInicio)
                && (vigenciaFim == null || !referencia.isAfter(vigenciaFim));
    }

    /**
     * Retorna a janela de apuração que contém a data de referência, limitada ao fim
     * da vigência. Vazio quando a meta não está vigente nessa data.
     */
    public Optional<JanelaMeta> janelaEm(LocalDate referencia) {
        if (!estaVigenteEm(referencia)) {
            return Optional.empty();
        }
        if (!periodicidade.isRecorrente()) {
            return Optional.of(new JanelaMeta(vigenciaInicio, vigenciaFim, 1));
        }
        JanelaMeta janela = periodicidade.janela(vigenciaInicio, referencia);
        LocalDate fim = vigenciaFim != null && vigenciaFim.isBefore(janela.fim()) ? vigenciaFim : janela.fim();
        return Optional.of(new JanelaMeta(janela.inicio(), fim, janela.ciclo()));
    }

    /**
     * Parcela da meta atribuível a um único dia: a quantidade exigida diluída pelos
     * dias da janela que contém a data. Permite comparar meta e realizado em qualquer
     * recorte (dia, mês, ano) mesmo quando ele não coincide com a janela — por exemplo,
     * uma meta mensal de 4 inspeções contribui com 4/31 em cada dia de um mês cheio.
     */
    public Optional<BigDecimal> metaDiariaEm(LocalDate referencia) {
        return janelaEm(referencia).map(janela -> BigDecimal.valueOf(quantidade)
                .divide(BigDecimal.valueOf(janela.dias()), ESCALA_RATEIO, RoundingMode.HALF_UP));
    }

    /** Indica se a vigência da meta alcança algum dia do intervalo informado. */
    public boolean vigenteEmAlgumDiaEntre(LocalDate inicio, LocalDate fim) {
        return isAtivo()
                && !vigenciaInicio.isAfter(fim)
                && (vigenciaFim == null || !vigenciaFim.isBefore(inicio));
    }

    /**
     * Indica se duas metas disputam o mesmo alvo em algum momento — duas metas
     * sobrepostas tornariam a cobrança ambígua.
     */
    public boolean conflitaCom(MetaInspecao outra) {
        return mesmoAlvo(outra) && Objects.equals(this.idTipoInspecao, outra.idTipoInspecao) && vigenciaSobrepoe(outra);
    }

    private boolean mesmoAlvo(MetaInspecao outra) {
        return escopo == outra.escopo
                && (escopo == EscopoMeta.FUNCAO
                        ? idFuncao.equals(outra.idFuncao)
                        : idColaborador.equals(outra.idColaborador));
    }

    private boolean vigenciaSobrepoe(MetaInspecao outra) {
        boolean comecaDepoisDoFimDaOutra = outra.vigenciaFim != null && vigenciaInicio.isAfter(outra.vigenciaFim);
        boolean terminaAntesDoInicioDaOutra = vigenciaFim != null && vigenciaFim.isBefore(outra.vigenciaInicio);
        return !comecaDepoisDoFimDaOutra && !terminaAntesDoInicioDaOutra;
    }

    private void aplicarDados(int quantidade, PeriodicidadeMeta periodicidade,
                              LocalDate vigenciaInicio, LocalDate vigenciaFim, String descricao) {
        if (quantidade <= 0) {
            throw new InvarianteVioladaException("A quantidade de inspeções da meta deve ser maior que zero.");
        }
        if (quantidade > QUANTIDADE_MAXIMA) {
            throw new InvarianteVioladaException(
                    "A quantidade de inspeções da meta não pode ultrapassar " + QUANTIDADE_MAXIMA + ".");
        }
        if (periodicidade == null) {
            throw new InvarianteVioladaException("A periodicidade da meta é obrigatória.");
        }
        if (vigenciaInicio == null) {
            throw new InvarianteVioladaException("A data de início da vigência da meta é obrigatória.");
        }
        if (vigenciaFim != null && vigenciaFim.isBefore(vigenciaInicio)) {
            throw new InvarianteVioladaException(
                    "A data de fim da vigência não pode ser anterior à data de início.");
        }
        if (!periodicidade.isRecorrente() && vigenciaFim == null) {
            throw new InvarianteVioladaException(
                    "A periodicidade PERSONALIZADO exige uma data de fim, pois a janela de apuração é a própria vigência.");
        }

        this.quantidade = quantidade;
        this.periodicidade = periodicidade;
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
        this.descricao = descricao == null || descricao.isBlank() ? null : descricao.trim();
    }

    private static EscopoMeta definirEscopo(UUID idFuncao, UUID idColaborador) {
        if (idFuncao != null && idColaborador != null) {
            throw new InvarianteVioladaException(
                    "A meta deve ser definida para uma função ou para um colaborador, nunca para os dois.");
        }
        if (idFuncao != null) {
            return EscopoMeta.FUNCAO;
        }
        if (idColaborador != null) {
            return EscopoMeta.COLABORADOR;
        }
        throw new InvarianteVioladaException("Informe a função ou o colaborador alvo da meta.");
    }
}
