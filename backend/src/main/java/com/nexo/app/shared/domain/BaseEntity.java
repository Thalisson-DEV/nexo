package com.nexo.app.shared.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nexo.app.shared.infrastructure.persistence.AuditListener;
import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Esta classe serve como uma <b>fundação<b/> para o <b>modelo de domínio rico.<b/>
 * Provê identificação única via <b>UUID, Auditoria Automática e Soft Delete.</b>
 *
 * @author <b>Thalisson<b/>
 * @version <b>1.1<b/>
 */
@Getter
@MappedSuperclass
@EntityListeners({AuditingEntityListener.class, AuditListener.class})
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Version
    @Column(name = "versao")
    private Long versao; // Para controle de concorrência

    @CreatedDate
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    @LastModifiedDate
    @Column(name = "data_ultima_alteracao")
    private OffsetDateTime dataUltimaAlteracao;

    @CreatedBy
    @Column(name = "usuario_criacao", updatable = false)
    private String usuarioCriacao;

    @LastModifiedBy
    @Column(name = "usuario_ultima_alteracao")
    private String usuarioUltimaAlteracao;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "data_desativacao")
    private OffsetDateTime dataDesativacao;

    @Transient
    @JsonIgnore
    private Map<String, Object> snapshotAnterior;

    /**
     * Define o snapshot anterior para auditoria.
     */
    public void setSnapshotAnterior(Map<String, Object> snapshot) {
        this.snapshotAnterior = snapshot;
    }

    public Map<String, Object> getSnapshotAnterior() {
        return this.snapshotAnterior;
    }

    /**
     * Lógica de soft delete integrada ao dominio rico.
     */
    public void desativar() {
        this.ativo = false;
        this.dataDesativacao = OffsetDateTime.now();
    }

    public void ativar() {
        this.ativo = true;
        this.dataDesativacao = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}