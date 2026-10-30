package com.nexo.app.modules.execucao.application.dto;

import com.nexo.app.modules.execucao.domain.TipoNota;
import com.nexo.app.modules.execucao.domain.OrigemMaterial;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexo.app.modules.execucao.domain.StatusExecucao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato do lançamento em etapa única com os aparelhos que ainda rodam versões
 * antigas do app de campo (fila offline com os nomes de antes).
 */
class ExecucaoLancamentoContratoTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    @DisplayName("Aceita o corpo antigo da fila offline (dataInicio/latitudeInicio) e ignora os campos de fim")
    void aceitaCorpoLegado() {
        Map<String, Object> corpoLegado = Map.of(
                "referenciaNota", "NOTA-1",
                "idEquipe", UUID.randomUUID().toString(),
                "dataInicio", "2026-10-09T10:15:00-03:00",
                "dataFim", "2026-10-09T10:15:00-03:00",
                "latitudeInicio", -12.97,
                "longitudeInicio", -38.5,
                "latitudeFim", -12.98);

        RegistrarExecucaoRequest request = mapper.convertValue(corpoLegado, RegistrarExecucaoRequest.class);

        assertThat(request.dataLancamento()).isEqualTo(OffsetDateTime.parse("2026-10-09T10:15:00-03:00"));
        assertThat(request.latitude()).isEqualByComparingTo("-12.97");
        assertThat(request.longitude()).isEqualByComparingTo("-38.5");
        assertThat(request.acoes()).isEmpty();
        assertThat(request.materiais()).isEmpty();
    }

    @Test
    @DisplayName("Aceita o corpo novo com dataLancamento/latitude/longitude")
    void aceitaCorpoNovo() {
        Map<String, Object> corpo = Map.of(
                "referenciaNota", "NOTA-2",
                "idEquipe", UUID.randomUUID().toString(),
                "dataLancamento", "2026-10-09T11:00:00-03:00",
                "latitude", -12.9,
                "longitude", -38.4);

        RegistrarExecucaoRequest request = mapper.convertValue(corpo, RegistrarExecucaoRequest.class);

        assertThat(request.dataLancamento()).isEqualTo(OffsetDateTime.parse("2026-10-09T11:00:00-03:00"));
        assertThat(request.latitude()).isEqualByComparingTo("-12.9");
    }

    @Test
    @DisplayName("A resposta traz a data de lançamento e, na transição, os aliases lidos pelo app antigo")
    void respostaComAliasesDeTransicao() {
        OffsetDateTime lancamento = OffsetDateTime.parse("2026-10-09T10:15:00-03:00");
        ExecucaoComercialResponse response = new ExecucaoComercialResponse(
                UUID.randomUUID(), "NOTA-1", UUID.randomUUID(), null, null, null,
                new BigDecimal("-12.97"), new BigDecimal("-38.5"), lancamento,
                StatusExecucao.CONCLUIDO, BigDecimal.TEN, null, null, List.of(), List.of(), null, null, null, 0);

        JsonNode json = mapper.valueToTree(response);

        assertThat(json.has("dataLancamento")).isTrue();
        assertThat(json.get("dataInicio").asText()).isEqualTo(json.get("dataLancamento").asText());
        assertThat(json.get("latitudeInicio").decimalValue()).isEqualByComparingTo("-12.97");
        assertThat(json.get("longitudeInicio").decimalValue()).isEqualByComparingTo("-38.5");
        assertThat(json.has("dataFim")).isFalse();
    }

    @Test
    @DisplayName("Corpo sem tipo e sem origem vira nota comum com material da equipe")
    void corpoSemTipoEhNotaComum() {
        Map<String, Object> corpo = Map.of(
                "referenciaNota", "NOTA-3",
                "idEquipe", UUID.randomUUID().toString(),
                "dataLancamento", "2026-10-09T11:00:00-03:00",
                "materiais", List.of(Map.of(
                        "idMaterial", UUID.randomUUID().toString(),
                        "quantidade", 1,
                        "operacao", "APLICADO")));

        RegistrarExecucaoRequest request = mapper.convertValue(corpo, RegistrarExecucaoRequest.class);

        assertThat(request.tipoNota()).isEqualTo(TipoNota.COMUM);
        assertThat(request.materiais().get(0).origem()).isEqualTo(OrigemMaterial.EQUIPE);
    }

    @Test
    @DisplayName("Ocorrência leva a origem de cada material")
    void ocorrenciaComOrigem() {
        Map<String, Object> corpo = Map.of(
                "referenciaNota", "OC-AB12",
                "tipoNota", "OCORRENCIA",
                "idEquipe", UUID.randomUUID().toString(),
                "dataLancamento", "2026-10-09T11:00:00-03:00",
                "materiais", List.of(Map.of(
                        "idMaterial", UUID.randomUUID().toString(),
                        "quantidade", 1,
                        "operacao", "RETIRADO",
                        "origem", "CONCESSIONARIA")));

        RegistrarExecucaoRequest request = mapper.convertValue(corpo, RegistrarExecucaoRequest.class);

        assertThat(request.tipoNota()).isEqualTo(TipoNota.OCORRENCIA);
        assertThat(request.materiais().get(0).origem()).isEqualTo(OrigemMaterial.CONCESSIONARIA);
    }

    @Test
    @DisplayName("Resposta marca a avulsa que ainda espera o número oficial")
    void respostaMarcaAvulsaAguardando() {
        ExecucaoComercialResponse aguardando = new ExecucaoComercialResponse(
                UUID.randomUUID(), "AV-1", UUID.randomUUID(), null, null, null, null, null,
                OffsetDateTime.now(), StatusExecucao.CONCLUIDO, BigDecimal.TEN, null, null,
                List.of(), List.of(), TipoNota.AVULSA, "AV-1", null, 0);

        JsonNode json = mapper.valueToTree(aguardando);

        assertThat(json.get("tipoNota").asText()).isEqualTo("AVULSA");
        assertThat(json.get("aguardandoNumeroOficial").asBoolean()).isTrue();
    }
}
