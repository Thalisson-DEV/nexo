package com.nexo.app.modules.sincronizacao.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class CasoSincronizacaoTest {
    @Test
    void cannotResolveWithoutJustification() {
        var operation = new OperacaoSincronizacao(UUID.randomUUID(), null,
                TipoOperacaoSincronizacao.EXECUCAO, UUID.randomUUID(), OffsetDateTime.now(), Map.of(), "hash");
        var syncCase = new CasoSincronizacao(operation, "Falha no envio");
        assertThatThrownBy(() -> syncCase.resolver(" ")).isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> syncCase.rejeitar(" ")).isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void discardClosesCaseAndPreventsFurtherChanges() {
        var operation = new OperacaoSincronizacao(UUID.randomUUID(), null,
                TipoOperacaoSincronizacao.SOLICITACAO, UUID.randomUUID(), OffsetDateTime.now(), Map.of(), "hash");
        var syncCase = new CasoSincronizacao(operation, "Quantidade invalida");
        syncCase.rejeitar("Envio de teste descartado pelo backoffice");
        assertThat(syncCase.getStatus()).isEqualTo(StatusCasoSincronizacao.REJEITADO);
        assertThat(syncCase.getJustificativaUltima()).isEqualTo("Envio de teste descartado pelo backoffice");
        assertThat(syncCase.getDataResolucao()).isNotNull();
        assertThatThrownBy(() -> syncCase.corrigir("nova versao")).isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> syncCase.atribuir(UUID.randomUUID())).isInstanceOf(InvarianteVioladaException.class);
    }
}
