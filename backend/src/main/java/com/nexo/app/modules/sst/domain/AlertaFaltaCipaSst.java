package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Alerta explicável do critério da quinta falta; nunca altera a composição sozinho. */
@Entity
@Table(name = "alertas_falta_cipa_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertaFaltaCipaSst extends BaseEntity {

    @Column(name = "id_corpo", nullable = false, updatable = false)
    private UUID idCorpo;

    @Column(name = "id_membro_corpo", nullable = false, updatable = false)
    private UUID idMembroCorpo;

    @Column(name = "id_reuniao_gatilho", nullable = false, updatable = false)
    private UUID idReuniaoGatilho;

    @Column(name = "quantidade_faltas", nullable = false)
    private int quantidadeFaltas;

    @Column(name = "emitido_em", nullable = false, updatable = false)
    private OffsetDateTime emitidoEm;

    @Column(name = "conferido_em")
    private OffsetDateTime conferidoEm;

    @Column(name = "id_usuario_conferencia")
    private UUID idUsuarioConferencia;

    public AlertaFaltaCipaSst(UUID idCorpo, UUID idMembroCorpo,
                              UUID idReuniaoGatilho, int quantidadeFaltas) {
        if (quantidadeFaltas < 5) {
            throw new InvarianteVioladaException("O alerta só pode ser emitido a partir da quinta falta.");
        }
        this.idCorpo = Objects.requireNonNull(idCorpo, "O mandato da CIPA é obrigatório.");
        this.idMembroCorpo = Objects.requireNonNull(idMembroCorpo, "O integrante é obrigatório.");
        this.idReuniaoGatilho = Objects.requireNonNull(idReuniaoGatilho, "A reunião gatilho é obrigatória.");
        this.quantidadeFaltas = quantidadeFaltas;
        this.emitidoEm = OffsetDateTime.now();
    }

    public void atualizarContagem(int quantidade) {
        if (quantidade > quantidadeFaltas) this.quantidadeFaltas = quantidade;
    }

    public void registrarConferencia(UUID idUsuario) {
        this.idUsuarioConferencia = Objects.requireNonNull(idUsuario,
                "Não foi possível identificar quem conferiu o alerta.");
        this.conferidoEm = OffsetDateTime.now();
    }
}
