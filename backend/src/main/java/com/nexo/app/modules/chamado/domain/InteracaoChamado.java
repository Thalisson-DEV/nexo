package com.nexo.app.modules.chamado.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
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

import java.time.OffsetDateTime;
import java.util.UUID;

/** Linha do tempo imutável do chamado, incluindo conversa e mudanças operacionais. */
@Entity
@Table(name = "interacoes_chamado")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InteracaoChamado extends BaseEntity {

    @JsonBackReference("chamado-interacoes")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_chamado", nullable = false)
    private Chamado chamado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoInteracaoChamado tipo;

    @Column(name = "mensagem", columnDefinition = "TEXT")
    private String mensagem;

    @Column(name = "interno", nullable = false)
    private boolean interno;

    @Column(name = "usuario_autor_id", nullable = false)
    private UUID usuarioAutorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", length = 30)
    private StatusChamado statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", length = 30)
    private StatusChamado statusNovo;

    @Column(name = "equipe_anterior_id")
    private UUID equipeAnteriorId;

    @Column(name = "equipe_nova_id")
    private UUID equipeNovaId;

    @Column(name = "responsavel_anterior_id")
    private UUID responsavelAnteriorId;

    @Column(name = "responsavel_novo_id")
    private UUID responsavelNovoId;

    @Column(name = "data_evento", nullable = false, updatable = false)
    private OffsetDateTime dataEvento;

    InteracaoChamado(Chamado chamado, TipoInteracaoChamado tipo, String mensagem,
                     boolean interno, UUID usuarioAutorId, StatusChamado statusAnterior,
                     StatusChamado statusNovo, UUID equipeAnteriorId, UUID equipeNovaId,
                     UUID responsavelAnteriorId, UUID responsavelNovoId) {
        this.chamado = chamado;
        this.tipo = tipo;
        this.mensagem = mensagem;
        this.interno = interno;
        this.usuarioAutorId = usuarioAutorId;
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        this.equipeAnteriorId = equipeAnteriorId;
        this.equipeNovaId = equipeNovaId;
        this.responsavelAnteriorId = responsavelAnteriorId;
        this.responsavelNovoId = responsavelNovoId;
        this.dataEvento = OffsetDateTime.now();
    }
}
