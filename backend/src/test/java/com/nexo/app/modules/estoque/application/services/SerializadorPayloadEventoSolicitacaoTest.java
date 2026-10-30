package com.nexo.app.modules.estoque.application.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.ReferenciaEventoSolicitacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SerializadorPayloadEventoSolicitacaoTest {

    private final SerializadorPayloadEventoSolicitacao serializador =
            new SerializadorPayloadEventoSolicitacao(
                    new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void serializaUuidDataReferenciaEDecimalDeFormaDeterministica() {
        UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        PayloadEventoSolicitacao primeiro = new PayloadEventoSolicitacao(
                "PLANEJAR",
                List.of(
                        new AlteracaoEventoSolicitacao("itens[1].quantidade", BigDecimal.TEN,
                                new BigDecimal("8.25")),
                        new AlteracaoEventoSolicitacao("dataPrevista", null, LocalDate.of(2026, 8, 21))),
                Map.of("obra", new ReferenciaEventoSolicitacao(id, "001 - OBRA TESTE")));
        PayloadEventoSolicitacao mesmaSemanticaEmOutraOrdem = new PayloadEventoSolicitacao(
                "PLANEJAR",
                List.of(
                        new AlteracaoEventoSolicitacao("dataPrevista", null, LocalDate.of(2026, 8, 21)),
                        new AlteracaoEventoSolicitacao("itens[1].quantidade", new BigDecimal("10.000"),
                                new BigDecimal("8.250"))),
                Map.of("obra", new ReferenciaEventoSolicitacao(id, "001 - OBRA TESTE")));

        String json = serializador.serializar(primeiro);

        assertThat(json).isEqualTo(serializador.serializar(mesmaSemanticaEmOutraOrdem));
        assertThat(json).contains(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                "2026-08-21", "10.000", "8.250");
        assertThat(json.indexOf("dataPrevista")).isLessThan(json.indexOf("itens[1].quantidade"));
    }

    @Test
    void removeCamposSensiveisRecursivamenteAntesDaSerializacaoCanonica() {
        PayloadEventoSolicitacao payload = new PayloadEventoSolicitacao(
                "CONFIRMAR_ENTREGA",
                List.of(
                        new AlteracaoEventoSolicitacao(
                                "operacao.assinaturaEquipe", "assinatura-anterior", "assinatura-nova"),
                        new AlteracaoEventoSolicitacao(
                                "planejamento.observacao",
                                Map.of("token", "token-anterior", "texto", "anterior"),
                                Map.of("senha", "senha-nova", "texto", "novo"))),
                Map.of(
                        "hashArquivo", "hash-publico-da-importacao",
                        "nivel1", Map.of(
                                "assinaturaAlmox", "assinatura-secreta",
                                "lista", List.of(Map.of(
                                        "conteudoCifrado", "ciphertext-secreto",
                                        "visivel", "preservado")))));

        String json = serializador.serializar(payload);

        assertThat(json)
                .contains("hashArquivo", "hash-publico-da-importacao", "texto", "novo", "preservado")
                .doesNotContain(
                        "assinaturaEquipe", "assinaturaAlmox", "assinatura-anterior", "assinatura-nova",
                        "token", "token-anterior", "senha", "senha-nova",
                "conteudoCifrado", "ciphertext-secreto");
    }

    @Test
    void canonicalizaJsonPersistidoComOrdemEespacosDoJsonb() {
        assertThat(SerializadorPayloadEventoSolicitacao.canonicalizarJson(
                "{ \"snapshots\" : { \"z\" : 2, \"a\" : 1 }, \"comando\" : \"TESTE\" }"))
                .isEqualTo("{\"comando\":\"TESTE\",\"snapshots\":{\"a\":1,\"z\":2}}");
    }

    private record ItemExemplo(String tipo, UUID id) {}

    @Test
    void serializaRecordEmSnapshotsDeFormaDeterministica() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PayloadEventoSolicitacao payload = new PayloadEventoSolicitacao(
                "CONFIRMAR_IMPORTACAO",
                List.of(),
                Map.of("solicitacoes", List.of(new ItemExemplo("222", id))));

        String json = serializador.serializar(payload);

        assertThat(json).contains("\"solicitacoes\":[{\"id\":\"11111111-2222-3333-4444-555555555555\",\"tipo\":\"222\"}]");
    }
}
