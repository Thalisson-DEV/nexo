package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Registro de troca do status operacional da obra; quem e quando vêm da auditoria da BaseEntity.
 */
@Entity
@Table(name = "historico_status_obras")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HistoricoStatusObra extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_obra", nullable = false)
    private Obra obra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_status_anterior")
    private StatusObra statusAnterior;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_status_novo", nullable = false)
    private StatusObra statusNovo;

    @Column(name = "observacao", length = 1000)
    private String observacao;

    HistoricoStatusObra(Obra obra, StatusObra statusAnterior, StatusObra statusNovo, String observacao) {
        this.obra = obra;
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        this.observacao = observacao;
    }
}
