package com.nexo.app.modules.estoque.application.services;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguracaoChavesAuditoriaSolicitacaoTest {

    @Test
    void falhaNoStartupQuandoChaveAtivaNaoFoiConfigurada() {
        ConfiguracaoChavesAuditoriaSolicitacao configuracao =
                new ConfiguracaoChavesAuditoriaSolicitacao();

        assertThatThrownBy(configuracao::afterPropertiesSet)
                .hasMessageContaining("versao ativa");
    }

    @Test
    void validaNoStartupUmaChaveExternaDePeloMenos256Bits() {
        ConfiguracaoChavesAuditoriaSolicitacao configuracao =
                new ConfiguracaoChavesAuditoriaSolicitacao();
        configuracao.setVersaoAtiva("v1");
        configuracao.setChaves(Map.of("v1", Base64.getEncoder().encodeToString(
                "chave-de-teste-com-mais-de-32-bytes-segura".getBytes(StandardCharsets.UTF_8))));

        assertThatCode(configuracao::afterPropertiesSet).doesNotThrowAnyException();
    }
}
