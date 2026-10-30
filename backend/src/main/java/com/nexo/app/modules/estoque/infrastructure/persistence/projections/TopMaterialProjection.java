package com.nexo.app.modules.estoque.infrastructure.persistence.projections;

public interface TopMaterialProjection {
    String getCodigo();
    String getNome();
    Long getTotalSolicitado();
}
