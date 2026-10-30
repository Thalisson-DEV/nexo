package com.nexo.app.modules.estoque.domain;

import com.nexo.app.shared.domain.BaseEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventoSolicitacaoMaterialTest {

    @Test
    void eventoAppendOnlyNaoHerdaBaseEntityMutavel() {
        assertThat(BaseEntity.class.isAssignableFrom(EventoSolicitacaoMaterial.class)).isFalse();
    }
}
