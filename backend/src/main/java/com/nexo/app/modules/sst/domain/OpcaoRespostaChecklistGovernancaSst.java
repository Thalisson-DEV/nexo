package com.nexo.app.modules.sst.domain;

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

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "respostas_opcoes_checklist_governanca_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpcaoRespostaChecklistGovernancaSst extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_resposta", nullable = false)
    private RespostaChecklistGovernancaSst resposta;

    @Column(name = "id_opcao", nullable = false)
    private UUID idOpcao;

    @Column(name = "opcao_snapshot", nullable = false, length = 255)
    private String opcaoSnapshot;

    @Column(name = "nao_conforme", nullable = false)
    private boolean naoConforme;

    @Enumerated(EnumType.STRING)
    @Column(name = "gravidade", length = 20)
    private GravidadeDesvio gravidade;

    OpcaoRespostaChecklistGovernancaSst(RespostaChecklistGovernancaSst resposta,
                                        RespostaChecklistGovernancaSst.NovaOpcao nova) {
        this.resposta = Objects.requireNonNull(resposta, "Resposta é obrigatória");
        this.idOpcao = Objects.requireNonNull(nova.idOpcao(), "Opção é obrigatória");
        if (nova.opcaoSnapshot() == null || nova.opcaoSnapshot().isBlank()) {
            throw new InvarianteVioladaException("O retrato da opção é obrigatório.");
        }
        this.opcaoSnapshot = nova.opcaoSnapshot().trim();
        this.naoConforme = nova.naoConforme();
        this.gravidade = nova.naoConforme() ? nova.gravidade() : null;
    }
}
