package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.EscopoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.EventoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.StatusIntegridadeHistorico;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegridadeHistoricoSolicitacaoServiceTest {

    @Test
    void selaEVerificaCadeiaIntegraComRotacaoDeChave() {
        ConfiguracaoChavesAuditoriaSolicitacao config = configuracao("v1", Map.of(
                "v1", segredo('a'), "v2", segredo('b')));
        IntegridadeHistoricoSolicitacaoService service =
                new IntegridadeHistoricoSolicitacaoService(
                        config, new ObservabilidadeSolicitacaoTecnica(new SimpleMeterRegistry()));
        UUID idSolicitacao = UUID.randomUUID();
        EventoSolicitacaoMaterial primeiro = evento(idSolicitacao, 1, "{\"valor\":\"1.000\"}");
        service.selar(primeiro, null);
        config.setVersaoAtiva("v2");
        EventoSolicitacaoMaterial segundo = evento(idSolicitacao, 2, "{\"valor\":\"2.000\"}");
        service.selar(segundo, primeiro.getHashAtual());

        var resultado = service.verificar(List.of(segundo, primeiro));

        assertThat(resultado.status()).isEqualTo(StatusIntegridadeHistorico.INTEGRO);
        assertThat(primeiro.getVersaoChave()).isEqualTo("v1");
        assertThat(segundo.getVersaoChave()).isEqualTo("v2");
    }

    @Test
    void informaNaoVerificadoQuandoChaveHistoricaNaoExiste() {
        ConfiguracaoChavesAuditoriaSolicitacao config = configuracao("v1", Map.of("v1", segredo('a')));
        IntegridadeHistoricoSolicitacaoService service =
                new IntegridadeHistoricoSolicitacaoService(
                        config, new ObservabilidadeSolicitacaoTecnica(new SimpleMeterRegistry()));
        EventoSolicitacaoMaterial evento = evento(UUID.randomUUID(), 1, "{}");
        service.selar(evento, null);
        config.setChaves(Map.of("v2", segredo('b')));

        assertThat(service.verificar(List.of(evento)).status())
                .isEqualTo(StatusIntegridadeHistorico.NAO_VERIFICADO);
    }

    @Test
    void detectaAdulteracaoDePayloadOuEncadeamento() {
        ConfiguracaoChavesAuditoriaSolicitacao config = configuracao("v1", Map.of("v1", segredo('a')));
        IntegridadeHistoricoSolicitacaoService service =
                new IntegridadeHistoricoSolicitacaoService(
                        config, new ObservabilidadeSolicitacaoTecnica(new SimpleMeterRegistry()));
        EventoSolicitacaoMaterial evento = evento(UUID.randomUUID(), 1, "{\"valor\":1}");
        service.selar(evento, null);
        ReflectionTestUtils.setField(evento, "payloadCanonico", "{\"valor\":2}");

        assertThat(service.verificar(List.of(evento)).status())
                .isEqualTo(StatusIntegridadeHistorico.VIOLADO);
    }

    @Test
    void aceitaPayloadQueFoiReserializadoPeloJsonbSemAfrouxarOHmac() {
        ConfiguracaoChavesAuditoriaSolicitacao config = configuracao("v1", Map.of("v1", segredo('a')));
        IntegridadeHistoricoSolicitacaoService service =
                new IntegridadeHistoricoSolicitacaoService(
                        config, new ObservabilidadeSolicitacaoTecnica(new SimpleMeterRegistry()));
        EventoSolicitacaoMaterial evento = evento(
                UUID.randomUUID(), 1, "{\"a\":1,\"b\":{\"a\":2,\"z\":3}}");
        service.selar(evento, null);

        // Simula jsonb_out: espacos e ordem de chaves diferente, mesmo valor.
        ReflectionTestUtils.setField(evento, "payloadCanonico",
                "{ \"b\" : { \"z\" : 3, \"a\" : 2 }, \"a\" : 1 }");

        assertThat(service.verificar(List.of(evento)).status())
                .isEqualTo(StatusIntegridadeHistorico.INTEGRO);
    }

    @Test
    void aceitaRoundTripDoPostgresqlComUtcEMicrossegundos() {
        ConfiguracaoChavesAuditoriaSolicitacao config = configuracao("v1", Map.of("v1", segredo('a')));
        IntegridadeHistoricoSolicitacaoService service =
                new IntegridadeHistoricoSolicitacaoService(
                        config, new ObservabilidadeSolicitacaoTecnica(new SimpleMeterRegistry()));
        EventoSolicitacaoMaterial evento = evento(UUID.randomUUID(), 1, "{\"valor\":1}");
        service.selar(evento, null);

        ReflectionTestUtils.setField(evento, "ocorridoEm", evento.getOcorridoEm().toInstant()
                .atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));

        assertThat(service.verificar(List.of(evento)).status())
                .isEqualTo(StatusIntegridadeHistorico.INTEGRO);
    }

    @Test
    void protegeAutorEMetadadosEDetectaLacunaDeSequencia() {
        ConfiguracaoChavesAuditoriaSolicitacao config = configuracao("v1", Map.of("v1", segredo('a')));
        IntegridadeHistoricoSolicitacaoService service =
                new IntegridadeHistoricoSolicitacaoService(
                        config, new ObservabilidadeSolicitacaoTecnica(new SimpleMeterRegistry()));
        UUID idSolicitacao = UUID.randomUUID();
        EventoSolicitacaoMaterial evento = evento(idSolicitacao, 1, "{}");
        service.selar(evento, null);
        ReflectionTestUtils.setField(evento, "autorSnapshot", "Outro autor");

        assertThat(service.verificar(List.of(evento)).status())
                .isEqualTo(StatusIntegridadeHistorico.VIOLADO);

        EventoSolicitacaoMaterial lacuna = evento(idSolicitacao, 2, "{}");
        service.selar(lacuna, null);
        ReflectionTestUtils.setField(lacuna, "sequencia", 3L);
        assertThat(service.verificar(List.of(lacuna)).status())
                .isEqualTo(StatusIntegridadeHistorico.VIOLADO);
    }

    @Test
    void rejeitaConfiguracaoFracaOuBase64Invalida() {
        ConfiguracaoChavesAuditoriaSolicitacao fraca = configuracao(
                "v1", Map.of("v1", Base64.getEncoder().encodeToString("curta".getBytes())));
        assertThatThrownBy(fraca::chaveAtiva).hasMessageContaining("256 bits");

        ConfiguracaoChavesAuditoriaSolicitacao invalida = configuracao(
                "v1", Map.of("v1", "nao-base64***"));
        assertThatThrownBy(invalida::chaveAtiva).hasMessageContaining("Base64");
    }

    private static EventoSolicitacaoMaterial evento(UUID idSolicitacao, long sequencia, String payload) {
        return new EventoSolicitacaoMaterial(
                idSolicitacao, sequencia, TipoEventoSolicitacao.PLANEJAMENTO_ALTERADO, null,
                TipoObraSnapshot.OBRA, null, null, UUID.randomUUID(), "Usuario",
                OrigemComandoTecnico.UI, null, 1, payload,
                OffsetDateTime.parse("2026-08-21T12:00:00-03:00").plusSeconds(sequencia));
    }

    private static ConfiguracaoChavesAuditoriaSolicitacao configuracao(
            String ativa,
            Map<String, String> chaves
    ) {
        ConfiguracaoChavesAuditoriaSolicitacao config = new ConfiguracaoChavesAuditoriaSolicitacao();
        config.setVersaoAtiva(ativa);
        config.setChaves(new LinkedHashMap<>(chaves));
        return config;
    }

    private static String segredo(char caractere) {
        return Base64.getEncoder().encodeToString(
                String.valueOf(caractere).repeat(32).getBytes(StandardCharsets.UTF_8));
    }
}
