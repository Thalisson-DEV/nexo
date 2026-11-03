package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

/**
 * Serviço ou material previsto para um ponto ou vão. A quantidade executada só muda por apontamento de execução.
 */
@Entity
@Table(name = "itens_planejados")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemPlanejado extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_kit_aplicado", nullable = false)
    private KitAplicado kitAplicado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoItemKit tipo;

    @Column(name = "id_acao")
    private UUID idAcao;

    @Column(name = "id_material")
    private UUID idMaterial;

    @Column(name = "codigo", length = 60)
    private String codigo;

    @Column(name = "descricao", length = 300)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "operacao", nullable = false, length = 20)
    private OperacaoItemKit operacao;

    @Column(name = "unidade", length = 20)
    private String unidade;

    @Column(name = "quantidade_kit", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantidadeKit;

    @Column(name = "quantidade_prevista", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantidadePrevista;

    @Column(name = "quantidade_executada", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantidadeExecutada = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusItemPlanejado status = StatusItemPlanejado.PENDENTE;

    ItemPlanejado(KitAplicado kitAplicado, KitAplicado.ModeloItem modelo, OperacaoItemKit operacao, BigDecimal fator) {
        this.kitAplicado = kitAplicado;
        this.tipo = modelo.tipo();
        this.idAcao = modelo.tipo() == TipoItemKit.SERVICO ? modelo.idReferencia() : null;
        this.idMaterial = modelo.tipo() == TipoItemKit.MATERIAL ? modelo.idReferencia() : null;
        this.codigo = modelo.codigo();
        this.descricao = modelo.descricao();
        this.operacao = operacao != null ? operacao : OperacaoItemKit.INSTALAR;
        this.unidade = modelo.unidade() != null && !modelo.unidade().isBlank()
                ? modelo.unidade().trim().toUpperCase(Locale.ROOT)
                : null;
        this.quantidadeKit = modelo.quantidade();
        recalcular(fator);
    }

    void recalcular(BigDecimal fator) {
        this.quantidadePrevista = quantidadeKit.multiply(fator).setScale(4, RoundingMode.HALF_UP);
    }

    void alterarPrevista(BigDecimal quantidade) {
        validarPendente("alterar");
        if (quantidade == null || quantidade.signum() <= 0) {
            throw new InvarianteVioladaException("A quantidade do item deve ser maior que zero.");
        }
        this.quantidadePrevista = quantidade;
    }

    void validarPendente(String acao) {
        if (status != StatusItemPlanejado.PENDENTE) {
            throw new InvarianteVioladaException("Não é possível " + acao + " o item " + descricao + ": ele está "
                    + status.name().toLowerCase(Locale.ROOT) + ".");
        }
    }

    public boolean programavel() {
        return isAtivo() && status == StatusItemPlanejado.PENDENTE && !kitAplicado.isExecutadoAntes();
    }

    public boolean temExecucao() {
        return quantidadeExecutada.signum() > 0;
    }

    public boolean concluido() {
        return status == StatusItemPlanejado.CONCLUIDO || status == StatusItemPlanejado.MEDIDO;
    }

    void programar() {
        if (!programavel()) {
            throw new InvarianteVioladaException("O item " + descricao + " não está pendente e não pode ser programado.");
        }
        this.status = StatusItemPlanejado.PROGRAMADO;
    }

    void desprogramar() {
        if (temExecucao()) {
            throw new InvarianteVioladaException("O item " + descricao + " já tem execução e não volta a pendente.");
        }
        this.status = StatusItemPlanejado.PENDENTE;
    }

    /**
     * Soma a quantidade executada e devolve a parte que passou do previsto neste apontamento.
     */
    BigDecimal apontar(BigDecimal quantidade) {
        if (status == StatusItemPlanejado.MEDIDO) {
            throw new InvarianteVioladaException("O item " + descricao + " já foi medido e não recebe apontamento.");
        }
        if (quantidade == null || quantidade.signum() <= 0) {
            throw new InvarianteVioladaException("A quantidade apontada de " + descricao + " deve ser maior que zero.");
        }
        BigDecimal excedenteAntes = quantidadeExecutada.subtract(quantidadePrevista).max(BigDecimal.ZERO);
        this.quantidadeExecutada = quantidadeExecutada.add(quantidade);
        atualizarStatusPelaExecucao();
        return quantidadeExecutada.subtract(quantidadePrevista).max(BigDecimal.ZERO).subtract(excedenteAntes);
    }

    void estornar(BigDecimal quantidade) {
        if (status == StatusItemPlanejado.MEDIDO) {
            throw new InvarianteVioladaException("O item " + descricao + " já foi medido e o apontamento não pode ser estornado.");
        }
        this.quantidadeExecutada = quantidadeExecutada.subtract(quantidade).max(BigDecimal.ZERO);
        atualizarStatusPelaExecucao();
    }

    private void atualizarStatusPelaExecucao() {
        if (quantidadeExecutada.signum() == 0) {
            this.status = StatusItemPlanejado.PROGRAMADO;
        } else {
            this.status = quantidadeExecutada.compareTo(quantidadePrevista) >= 0
                    ? StatusItemPlanejado.CONCLUIDO
                    : StatusItemPlanejado.EM_ANDAMENTO;
        }
    }

    /** Só serviço concluído pela execução apontada entra em medição; o kit executado antes do sistema fica de fora. */
    public boolean medivel() {
        return isAtivo() && tipo == TipoItemKit.SERVICO && status == StatusItemPlanejado.CONCLUIDO
                && !kitAplicado.isExecutadoAntes();
    }

    void medir() {
        if (!medivel()) {
            throw new InvarianteVioladaException("O item " + descricao + " não é um serviço concluído pendente de medição.");
        }
        this.status = StatusItemPlanejado.MEDIDO;
    }

    void desfazerMedicao() {
        if (status == StatusItemPlanejado.MEDIDO) {
            this.status = StatusItemPlanejado.CONCLUIDO;
        }
    }

    void concluirAntesDoSistema() {
        this.status = StatusItemPlanejado.CONCLUIDO;
        this.quantidadeExecutada = quantidadePrevista;
    }

    void voltarParaPendente() {
        this.status = StatusItemPlanejado.PENDENTE;
        this.quantidadeExecutada = BigDecimal.ZERO;
    }
}
