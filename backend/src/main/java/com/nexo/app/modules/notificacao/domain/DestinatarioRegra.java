package com.nexo.app.modules.notificacao.domain;

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
 * Quem uma regra notifica. Vários destinatários somam (OR), como nos segmentos do
 * mural.
 */
@Entity
@Table(name = "destinatarios_regra_notificacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DestinatarioRegra extends BaseEntity {

    @JsonBackReference("regra-destinatarios")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_regra", nullable = false)
    private RegraNotificacao regra;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoDestinatarioRegra tipo;

    /** Perfil, usuário ou política, conforme o tipo. */
    @Column(name = "id_alvo")
    private UUID idAlvo;

    /** Nome da permissão ou do papel dinâmico, conforme o tipo. */
    @Column(name = "valor_alvo", length = 120)
    private String valorAlvo;

    DestinatarioRegra(RegraNotificacao regra, TipoDestinatarioRegra tipo, UUID idAlvo, String valorAlvo) {
        if (regra == null) {
            throw new InvarianteVioladaException("A regra do destinatário é obrigatória.");
        }
        if (tipo == null) {
            throw new InvarianteVioladaException("Escolha o tipo de destinatário.");
        }
        if (tipo.exigeIdAlvo() && idAlvo == null) {
            throw new InvarianteVioladaException(
                    "O destinatário '" + tipo.getRotulo() + "' exige que o alvo seja identificado.");
        }
        if (tipo.exigeValorAlvo() && !StringUtils.hasText(valorAlvo)) {
            throw new InvarianteVioladaException(
                    "O destinatário '" + tipo.getRotulo() + "' exige que o valor seja informado.");
        }

        this.regra = regra;
        this.tipo = tipo;
        this.idAlvo = tipo.exigeIdAlvo() ? idAlvo : null;
        this.valorAlvo = tipo.exigeValorAlvo() ? valorAlvo.trim() : null;
    }
}
