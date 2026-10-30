package com.nexo.app.shared.application.analytics;

import java.util.List;

/** Publica contagens cadastrais seguras pertencentes a um modulo do Nexo. */
public interface SemanticResourceCountProvider {

    List<ResourceCountDescriptor> descriptors();

    long count(String resourceId, SemanticQueryContext context);

    record ResourceCountDescriptor(
            String id,
            String nome,
            String descricao,
            String autoridadeObrigatoria) {
    }
}
