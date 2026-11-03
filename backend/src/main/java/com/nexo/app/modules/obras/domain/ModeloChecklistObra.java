package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

/**
 * Checklist configurável da documentação técnica: a visita prévia e a ficha As Built usam o mesmo desenho, e só a ficha
 * pede fotos para o book. Depois de usado, a estrutura não muda; para alterar, duplica-se o modelo.
 */
@Entity
@Table(name = "modelos_checklist_obra")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModeloChecklistObra extends BaseEntity {

    public enum Finalidade {
        VISITA_PREVIA,
        AS_BUILT
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "finalidade", nullable = false, length = 20, updatable = false)
    private Finalidade finalidade;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "estrutura", nullable = false, columnDefinition = "jsonb")
    private EstruturaChecklist estrutura;

    public ModeloChecklistObra(Finalidade finalidade, String titulo, String descricao, EstruturaChecklist estrutura) {
        this.finalidade = Objects.requireNonNull(finalidade);
        atualizarDados(titulo, descricao);
        alterarEstrutura(estrutura);
    }

    public void atualizarDados(String titulo, String descricao) {
        if (titulo == null || titulo.isBlank()) {
            throw new InvarianteVioladaException("Informe o título do modelo.");
        }
        this.titulo = titulo.trim();
        this.descricao = descricao != null && !descricao.isBlank() ? descricao.trim() : null;
    }

    public void alterarEstrutura(EstruturaChecklist estrutura) {
        if (estrutura == null) {
            throw new InvarianteVioladaException("Inclua ao menos um grupo no modelo.");
        }
        estrutura.validar();
        if (finalidade != Finalidade.AS_BUILT && !estrutura.evidencias().isEmpty()) {
            throw new InvarianteVioladaException("Só o modelo de ficha As Built pede fotos para o book.");
        }
        this.estrutura = estrutura;
    }
}
