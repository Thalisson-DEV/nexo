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
import java.util.Locale;
import java.util.UUID;

/**
 * Item de um kit construtivo: um serviço (Ação do contrato) ou um material, com quantidade por kit.
 */
@Entity
@Table(name = "itens_kits_construtivos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemKit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_kit", nullable = false)
    private KitConstrutivo kit;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoItemKit tipo;

    @Column(name = "id_acao")
    private UUID idAcao;

    @Column(name = "id_material")
    private UUID idMaterial;

    @Enumerated(EnumType.STRING)
    @Column(name = "operacao", nullable = false, length = 20)
    private OperacaoItemKit operacao;

    @Column(name = "unidade", length = 20)
    private String unidade;

    @Column(name = "quantidade", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantidade;

    ItemKit(KitConstrutivo kit, TipoItemKit tipo, UUID idReferencia, OperacaoItemKit operacao,
            String unidade, BigDecimal quantidade) {
        if (tipo == null) {
            throw new InvarianteVioladaException("O tipo do item do kit é obrigatório.");
        }
        if (idReferencia == null) {
            throw new InvarianteVioladaException(tipo == TipoItemKit.SERVICO
                    ? "O item de serviço deve apontar para uma ação do contrato."
                    : "O item de material deve apontar para um material.");
        }
        this.kit = kit;
        this.tipo = tipo;
        this.idAcao = tipo == TipoItemKit.SERVICO ? idReferencia : null;
        this.idMaterial = tipo == TipoItemKit.MATERIAL ? idReferencia : null;
        alterar(operacao, unidade, quantidade);
    }

    void alterar(OperacaoItemKit operacao, String unidade, BigDecimal quantidade) {
        if (quantidade == null || quantidade.signum() <= 0) {
            throw new InvarianteVioladaException("A quantidade do item do kit deve ser maior que zero.");
        }
        this.operacao = operacao != null ? operacao : OperacaoItemKit.INSTALAR;
        this.unidade = unidade != null && !unidade.isBlank() ? unidade.trim().toUpperCase(Locale.ROOT) : null;
        this.quantidade = quantidade;
    }

    public UUID getIdReferencia() {
        return tipo == TipoItemKit.SERVICO ? idAcao : idMaterial;
    }

    public boolean referencia(TipoItemKit tipo, UUID idReferencia, OperacaoItemKit operacao) {
        OperacaoItemKit alvo = operacao != null ? operacao : OperacaoItemKit.INSTALAR;
        return this.tipo == tipo && idReferencia != null && idReferencia.equals(getIdReferencia())
                && this.operacao == alvo;
    }
}
