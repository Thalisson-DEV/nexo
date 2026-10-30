package com.nexo.app.modules.estoque.application.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.application.dto.ProblemaHttpResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SolicitacaoTecnicaFixtureContractTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void exemplosDoGuiaDesserializamEAtendemBeanValidation(
            String arquivo,
            JavaType tipo
    ) throws Exception {
        String recurso = "/fixtures/solicitacoes-tecnicas/" + arquivo;
        try (InputStream entrada = getClass().getResourceAsStream(recurso)) {
            assertThat(entrada).as("fixture %s", recurso).isNotNull();
            Object dto = objectMapper.readValue(entrada, tipo);
            assertThat(validator.validate(dto)).isEmpty();
        }
    }

    @Test
    void detalheTecnicoEComercialPreservamSemanticasDistintas() throws Exception {
        SolicitacaoResponse tecnico = ler("detalhe-response.json", SolicitacaoResponse.class);
        SolicitacaoResponse comercial = ler("comercial-detalhe-response.json", SolicitacaoResponse.class);

        assertThat(tecnico.tecnico()).isNotNull();
        assertThat(tecnico.contexto()).isEqualTo(ContextoSolicitacaoMaterial.TECNICO);
        assertThat(tecnico.versao()).isEqualTo(7L);
        assertThat(tecnico.statusSeparacao()).isNotNull();
        assertThat(tecnico.acoesPermitidas()).isNotEmpty();
        assertThat(tecnico.tecnico().tipoTecnico()).isEqualTo(
                com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica.ADICAO_COMUM);
        assertThat(tecnico.tecnico().atrasada()).isFalse();
        assertThat(tecnico.tecnico().reserva()).isNull();
        assertThat(tecnico.tecnico().operacoes())
                .extracting(DetalhesTecnicosSolicitacaoResponse.OperacaoResponse::tipo)
                .containsExactly(
                        com.nexo.app.modules.estoque.domain.TipoOperacaoSolicitacao.ENTREGA,
                        com.nexo.app.modules.estoque.domain.TipoOperacaoSolicitacao.ESTORNO);
        assertThat(comercial.tecnico()).isNull();
        assertThat(comercial.contexto()).isEqualTo(ContextoSolicitacaoMaterial.COMERCIAL);
        assertThat(comercial.versao()).isEqualTo(5L);
        assertThat(comercial.statusSeparacao()).isNotNull();
        assertThat(comercial.acoesPermitidas()).isNotEmpty();
        assertThat(comercial.idEntregadorAlmox()).isNotNull();
        assertThat(comercial.itens().getFirst().quantidadeEntregue()).isEqualByComparingTo("1.500");
    }

    @Test
    void movimentosTecnicosPreservamOrigemECorrelacaoOperacional() throws Exception {
        MovimentacaoEstoqueResponse movimento = ler("movimentacao-response.json", MovimentacaoEstoqueResponse.class);
        MovimentacaoOperacaoResponse operacao = ler(
                "movimentacao-operacao-response.json", MovimentacaoOperacaoResponse.class);

        assertThat(movimento.contexto()).isEqualTo(ContextoSolicitacaoMaterial.TECNICO);
        assertThat(movimento.idObra()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(movimento.tipoObra()).isNotNull();
        assertThat(movimento.ocorrencia()).isEqualTo("OC 1042");
        assertThat(movimento.idOperacao()).isEqualTo(movimento.idReferenciaOperacao());
        assertThat(movimento.idItemOperacao()).isNotNull();
        assertThat(operacao.contexto()).isEqualTo(ContextoSolicitacaoMaterial.TECNICO);
        assertThat(operacao.idSolicitacao()).isEqualTo(movimento.idSolicitacao());
        assertThat(operacao.itens().getFirst().idItemOperacao()).isEqualTo(movimento.idItemOperacao());
        assertThat(operacao.itens().getFirst().idItemSolicitacao()).isNotNull();
    }

    @Test
    void replanejamentoDevolveSnapshotAutocontidoComNovaVersaoEAcoes() throws Exception {
        PlanejamentoSolicitacaoTecnicaResponse resposta = ler(
                "planejamento-response.json", PlanejamentoSolicitacaoTecnicaResponse.class);

        assertThat(resposta.solicitacaoAtualizada()).isNotNull();
        assertThat(resposta.solicitacaoAtualizada().versao()).isEqualTo(4L);
        assertThat(resposta.solicitacaoAtualizada().tecnico()).isNotNull();
        assertThat(resposta.solicitacaoAtualizada().tecnico().acoesPermitidas()).isNotEmpty();
    }

    @Test
    void conflitoDeVersaoInformaRecursoEVersoesParaRecarregamento() throws Exception {
        ProblemaHttpResponse problema = ler(
                "problema-versao-desatualizada.json", ProblemaHttpResponse.class);

        assertThat(problema.status()).isEqualTo(409);
        assertThat(problema.code()).isEqualTo("VERSAO_DESATUALIZADA");
        assertThat(problema.recurso()).isEqualTo("SOLICITACAO");
        assertThat(problema.versaoEsperada()).isEqualTo(2L);
        assertThat(problema.versaoAtual()).isEqualTo(3L);
    }

    @Test
    void snapshotDiferenciaOmissaoDeLimpezaExplicita() throws Exception {
        String camposObrigatorios = """
                {"versao":3,
                 "idObra":"11111111-1111-1111-1111-111111111111",
                 "idAlmoxarifadoAtendimento":"22222222-2222-2222-2222-222222222222",
                "itens":[{"idItem":null,"idMaterial":"44444444-4444-4444-4444-444444444444","tipoMovimentacao":"ADICAO_COMUM","quantidade":1.000}]%s}
                """;
        PlanejarSolicitacaoTecnicaRequest omitido = objectMapper.readValue(
                camposObrigatorios.formatted(""), PlanejarSolicitacaoTecnicaRequest.class);
        PlanejarSolicitacaoTecnicaRequest limpeza = objectMapper.readValue(
                camposObrigatorios.formatted(
                        ",\"ocorrencia\":null,\"idEquipe\":null,\"dataPrevista\":null,\"observacao\":null"),
                PlanejarSolicitacaoTecnicaRequest.class);

        assertThat(validator.validate(omitido))
                .extracting(violacao -> violacao.getPropertyPath().toString())
                .containsExactlyInAnyOrder("ocorrencia", "idEquipe", "dataPrevista", "observacao");
        assertThat(validator.validate(limpeza)).isEmpty();
        assertThat(limpeza.ocorrencia()).isNull();
        assertThat(limpeza.idEquipe()).isNull();
        assertThat(limpeza.dataPrevista()).isNull();
        assertThat(limpeza.observacao()).isNull();
    }

    private <T> T ler(String arquivo, Class<T> tipo) throws Exception {
        String recurso = "/fixtures/solicitacoes-tecnicas/" + arquivo;
        try (InputStream entrada = getClass().getResourceAsStream(recurso)) {
            assertThat(entrada).as("fixture %s", recurso).isNotNull();
            return objectMapper.readValue(entrada, tipo);
        }
    }

    static Stream<Arguments> fixtures() {
        TypeFactory tipos = TypeFactory.defaultInstance();
        return Stream.of(
                contrato(tipos, "criacao-simples.json", CriarSolicitacaoTecnicaRequest.class),
                contrato(tipos, "criacao-mista.json", CriarSolicitacaoTecnicaRequest.class),
                contrato(tipos, "criacao-response.json", CriacaoSolicitacaoTecnicaResponse.class),
                contrato(tipos, "planejamento-reagrupamento.json", PlanejarSolicitacaoTecnicaRequest.class),
                contrato(tipos, "planejamento-response.json", PlanejamentoSolicitacaoTecnicaResponse.class),
                contrato(tipos, "reserva.json", RegistrarReservaTecnicaRequest.class),
                contrato(tipos, "separacao.json", AlterarSeparacaoTecnicaRequest.class),
                contrato(tipos, "entrega.json", ConfirmarEntregaTecnicaRequest.class),
                contrato(tipos, "devolucao.json", ConfirmarDevolucaoTecnicaRequest.class),
                contrato(tipos, "estorno.json", EstornarOperacaoTecnicaRequest.class),
                contrato(tipos, "operacao-estorno-response.json", OperacaoTecnicaResponse.class),
                contrato(tipos, "encerramento.json", EncerrarSaldoTecnicoRequest.class),
                contrato(tipos, "listagem-response.json",
                        tipos.constructParametricType(PageResponse.class, SolicitacaoSummaryResponse.class)),
                contrato(tipos, "detalhe-response.json", SolicitacaoResponse.class),
                contrato(tipos, "movimentacao-response.json", MovimentacaoEstoqueResponse.class),
                contrato(tipos, "movimentacao-operacao-response.json", MovimentacaoOperacaoResponse.class),
                contrato(tipos, "movimentacoes-listagem-response.json",
                        tipos.constructParametricType(PageResponse.class, MovimentacaoOperacaoSummaryResponse.class)),
                contrato(tipos, "historico-response.json", HistoricoSolicitacaoResponse.class),
                contrato(tipos, "problema-versao-desatualizada.json", ProblemaHttpResponse.class),
                contrato(tipos, "importacao-confirmacao.json", ConfirmarImportacaoSolicitacaoTecnicaRequest.class),
                contrato(tipos, "importacao-previa-response.json", ImportacaoSolicitacaoTecnicaResponse.class),
                contrato(tipos, "importacao-confirmacao-response.json",
                        ConfirmacaoImportacaoSolicitacaoTecnicaResponse.class),
                contrato(tipos, "comercial-criacao.json", SolicitacaoRequest.class),
                contrato(tipos, "comercial-entrega.json", EntregaSolicitacaoRequest.class),
                contrato(tipos, "comercial-estorno-parcial.json", EstornoParcialRequest.class),
                contrato(tipos, "comercial-operacao-response.json", UUID.class),
                contrato(tipos, "comercial-listagem-response.json",
                        tipos.constructParametricType(PageResponse.class, SolicitacaoSummaryResponse.class)),
                contrato(tipos, "comercial-detalhe-response.json", SolicitacaoResponse.class));
    }

    private static Arguments contrato(TypeFactory tipos, String arquivo, Class<?> tipo) {
        return Arguments.of(arquivo, tipos.constructType(tipo));
    }

    private static Arguments contrato(TypeFactory tipos, String arquivo, JavaType tipo) {
        return Arguments.of(arquivo, tipo);
    }
}
