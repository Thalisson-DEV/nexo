package com.nexo.app.modules.notificacao.domain;

import java.util.Objects;
import java.util.UUID;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonBackReference;
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

/**
 * Uma fatia do público-alvo de um comunicado. Vários segmentos se somam: o
 * comunicado alcança quem satisfaz <em>qualquer</em> um deles.
 */
@Entity
@Table(name = "segmentos_comunicado")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SegmentoComunicado extends BaseEntity {

    @JsonBackReference("comunicado-segmentos")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_comunicado", nullable = false)
    private Comunicado comunicado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_alvo", nullable = false, length = 20)
    private TipoAlvoSegmento tipoAlvo;

    @Column(name = "id_alvo")
    private UUID idAlvo;

    @Column(name = "valor_alvo", length = 50)
    private String valorAlvo;

    SegmentoComunicado(Comunicado comunicado, TipoAlvoSegmento tipoAlvo, UUID idAlvo, String valorAlvo) {
        if (comunicado == null) {
            throw new InvarianteVioladaException("O comunicado do público-alvo é obrigatório.");
        }
        if (tipoAlvo == null) {
            throw new InvarianteVioladaException("Informe o tipo de público-alvo.");
        }
        if (tipoAlvo.exigeIdAlvo() && idAlvo == null) {
            throw new InvarianteVioladaException(
                    "O público '" + tipoAlvo.getRotulo() + "' exige que o alvo seja identificado.");
        }
        if (tipoAlvo.exigeValorAlvo() && !StringUtils.hasText(valorAlvo)) {
            throw new InvarianteVioladaException(
                    "O público '" + tipoAlvo.getRotulo() + "' exige que o valor seja informado.");
        }

        this.comunicado = comunicado;
        this.tipoAlvo = tipoAlvo;
        // Os campos não usados pelo tipo ficam nulos — é o que o CHECK da V74 cobra.
        this.idAlvo = tipoAlvo.exigeIdAlvo() ? idAlvo : null;
        this.valorAlvo = tipoAlvo.exigeValorAlvo() ? valorAlvo.trim().toUpperCase() : null;
    }

    boolean corresponde(TipoAlvoSegmento tipoAlvo, UUID idAlvo, String valorAlvo) {
        if (this.tipoAlvo != tipoAlvo) {
            return false;
        }
        return Objects.equals(this.idAlvo, tipoAlvo.exigeIdAlvo() ? idAlvo : null)
                && Objects.equals(this.valorAlvo, tipoAlvo.exigeValorAlvo() && valorAlvo != null
                        ? valorAlvo.trim().toUpperCase() : null);
    }
}
