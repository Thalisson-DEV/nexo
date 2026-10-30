package com.nexo.app.modules.estoque.application.usecases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nexo.app.modules.estoque.application.dto.HistoricoSolicitacaoResponse;
import com.nexo.app.modules.estoque.application.services.ConfiguracaoChavesAuditoriaSolicitacao;
import com.nexo.app.modules.estoque.application.services.IntegridadeHistoricoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.ResolvedorAutorEventoSolicitacao;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.EscopoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.EventoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.infrastructure.persistence.EventoSolicitacaoMaterialRepository;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoricoSolicitacaoUseCasesTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void retornaPaginaEstavelComIntegridadeESemCamposSensiveis() throws Exception {
        EventoSolicitacaoMaterialRepository repository = mock(EventoSolicitacaoMaterialRepository.class);
        UUID idSolicitacao = UUID.randomUUID();
        EventoSolicitacaoMaterial evento = evento(idSolicitacao, 1, "{\"assinaturaEquipe\":\"segredo\",\"valor\":\"visivel\"}");
        IntegridadeHistoricoSolicitacaoService integridade = integridade();
        integridade.selar(evento, null);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(evento), invocation.getArgument(1), 1));
        when(repository.findAllByEscopoAndIdSolicitacaoOrderBySequenciaAsc(
                EscopoEventoSolicitacao.SOLICITACAO, idSolicitacao)).thenReturn(List.of(evento));

        ResolvedorAutorEventoSolicitacao resolvedorAutor = mock(ResolvedorAutorEventoSolicitacao.class);
        when(resolvedorAutor.resolver(any(), any())).thenAnswer(invocacao -> invocacao.getArgument(1));
        HistoricoSolicitacaoUseCases useCases = new HistoricoSolicitacaoUseCases(
                repository, integridade, resolvedorAutor, objectMapper);

        HistoricoSolicitacaoResponse response = useCases.porSolicitacao(idSolicitacao, null, PageRequest.of(0, 200));

        assertThat(response.page().getSize()).isEqualTo(100);
        assertThat(response.eventos()).singleElement().satisfies(retornado -> {
            assertThat(retornado.tipoTecnico()).isEqualTo(TipoSolicitacaoTecnica.ADICAO_COMUM);
            assertThat(retornado.payload()).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = (Map<String, Object>) retornado.payload();
            assertThat(payloadMap).doesNotContainKey("assinaturaEquipe");
            assertThat(payloadMap).containsEntry("valor", "visivel");
        });
        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"valor\":\"visivel\"");
        assertThat(json).doesNotContain("\"array\":false", "\"nodeType\"");
        assertThat(response.integridade()).singleElement().satisfies(cadeia ->
                assertThat(cadeia.status()).isEqualTo(com.nexo.app.modules.estoque.domain.StatusIntegridadeHistorico.INTEGRO));
    }

    private static EventoSolicitacaoMaterial evento(UUID idSolicitacao, long sequencia, String payload) {
        EventoSolicitacaoMaterial evento = new EventoSolicitacaoMaterial(
                idSolicitacao, sequencia,
                TipoEventoSolicitacao.ENTREGA_CONFIRMADA, TipoSolicitacaoTecnica.ADICAO_COMUM,
                TipoObraSnapshot.CORRETIVA, UUID.randomUUID(), "OC-1042",
                UUID.randomUUID(), "Usuário de teste",
                OrigemComandoTecnico.UI, null, 4L, payload, OffsetDateTime.parse("2026-08-21T15:00:00Z"));
        ReflectionTestUtils.setField(evento, "id", UUID.randomUUID());
        return evento;
    }

    private static IntegridadeHistoricoSolicitacaoService integridade() {
        ConfiguracaoChavesAuditoriaSolicitacao configuracao = new ConfiguracaoChavesAuditoriaSolicitacao();
        configuracao.setVersaoAtiva("v1");
        configuracao.setChaves(Map.of("v1", Base64.getEncoder().encodeToString(
                "chave-de-teste-com-32-bytes-00000".getBytes(StandardCharsets.UTF_8))));
        return new IntegridadeHistoricoSolicitacaoService(
                configuracao, new ObservabilidadeSolicitacaoTecnica(new SimpleMeterRegistry()));
    }
}