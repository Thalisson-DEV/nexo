package com.nexo.app.modules.telemetria.application.usecases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.telemetria.application.dto.TelemetriaMobileDtos.EventoRequest;
import com.nexo.app.modules.telemetria.application.dto.TelemetriaMobileDtos.LoteRequest;
import com.nexo.app.modules.telemetria.infrastructure.persistence.TelemetriaMobileRepository;
import com.nexo.app.modules.telemetria.infrastructure.persistence.TelemetriaMobileRepository.Linha;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class TelemetriaMobileUseCasesTest {

    private static final Instant AGORA = Instant.parse("2026-10-08T15:00:00Z");
    private TelemetriaMobileRepository repository;
    private TelemetriaMobileUseCases useCases;

    @BeforeEach
    void setUp() {
        repository = mock(TelemetriaMobileRepository.class);
        when(repository.inserir(anyList())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());
        useCases = new TelemetriaMobileUseCases(repository, new ObjectMapper(), Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static OffsetDateTime ha(long segundos) {
        return OffsetDateTime.ofInstant(AGORA.minusSeconds(segundos), ZoneOffset.UTC);
    }

    @Test
    void gravaEventosComAparelhoEVersaoSemNenhumVinculoComUsuario() {
        var r = useCases.receber(new LoteRequest("aparelho-abc", "a1b2c3d4", List.of(
                new EventoRequest("retomada.decisao", ha(10), Map.of("acao", "retomar", "reserva", false)))));

        assertThat(r.recebidos()).isEqualTo(1);
        @SuppressWarnings("unchecked") ArgumentCaptor<List<Linha>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).inserir(captor.capture());
        Linha linha = captor.getValue().getFirst();
        assertThat(linha.tipo()).isEqualTo("retomada.decisao");
        assertThat(linha.idAparelho()).isEqualTo("aparelho-abc");
        assertThat(linha.versaoApp()).isEqualTo("a1b2c3d4");
        assertThat(linha.dadosJson()).contains("\"acao\":\"retomar\"").contains("\"reserva\":false");
    }

    @Test
    void descartaEventosForaDaJanelaDeTempo() {
        var r = useCases.receber(new LoteRequest(null, null, List.of(
                new EventoRequest("app.abertura", ha(31L * 24 * 3600), Map.of()),
                new EventoRequest("app.abertura", ha(-2L * 24 * 3600), Map.of()),
                new EventoRequest("app.abertura", ha(60), Map.of()))));

        assertThat(r.recebidos()).isEqualTo(1);
        assertThat(r.descartados()).isEqualTo(2);
    }

    @Test
    void loteTodoForaDaJanelaNaoGravaNada() {
        var r = useCases.receber(new LoteRequest(null, null, List.of(
                new EventoRequest("app.abertura", ha(90L * 24 * 3600), Map.of()))));
        assertThat(r.recebidos()).isZero();
        verify(repository).inserir(List.of());
    }

    @Test
    void sanitizacaoMantemSoPrimitivosEChavesCurtas() {
        Map<String, Object> dados = new HashMap<>();
        dados.put("motivo", "x".repeat(500));
        dados.put("ms", 1234);
        dados.put("ok", true);
        dados.put("objeto", Map.of("cpf", "123.456.789-00"));
        dados.put("lista", List.of("nome"));
        dados.put("chave com espaço", "v");
        dados.put("1invalida", "v");
        dados.put("nulo", null);

        Map<String, Object> limpo = TelemetriaMobileUseCases.sanitizar(dados);

        assertThat(limpo).containsOnlyKeys("motivo", "ms", "ok");
        assertThat((String) limpo.get("motivo")).hasSize(TelemetriaMobileUseCases.MAX_TEXTO);
    }

    @Test
    void limitaQuantidadeDeChaves() {
        Map<String, Object> dados = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            dados.put("k" + i, i);
        }
        assertThat(TelemetriaMobileUseCases.sanitizar(dados)).hasSize(TelemetriaMobileUseCases.MAX_CHAVES);
    }

    @Test
    void dadosAusentesViramObjetoVazio() {
        assertThat(TelemetriaMobileUseCases.sanitizar(null)).isEmpty();
    }

    @Test
    void expurgoUsaRetencaoDe90Dias() {
        when(repository.expurgarAntesDe(any())).thenReturn(3);
        useCases.expurgar();
        verify(repository).expurgarAntesDe(OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC).minusDays(90));
    }
}
