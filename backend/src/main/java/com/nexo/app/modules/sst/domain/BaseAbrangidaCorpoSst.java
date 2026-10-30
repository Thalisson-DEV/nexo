package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "bases_abrangidas_corpo_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BaseAbrangidaCorpoSst extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_corpo", nullable = false)
    private CorpoGovernancaSst corpo;

    @Column(name = "id_base_operacional", nullable = false)
    private UUID idBaseOperacional;

    @Column(name = "base_nome", nullable = false, length = 120)
    private String baseNome;

    BaseAbrangidaCorpoSst(CorpoGovernancaSst corpo, UUID idBaseOperacional, String baseNome) {
        if (corpo == null || idBaseOperacional == null || baseNome == null || baseNome.isBlank()) {
            throw new InvarianteVioladaException("A base abrangida deve possuir identificação e nome válidos.");
        }
        this.corpo = corpo;
        this.idBaseOperacional = idBaseOperacional;
        this.baseNome = baseNome.trim();
    }

    public UUID getIdCorpo() {
        return corpo == null ? null : corpo.getId();
    }
}
