package com.nexo.app.modules.estoque.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.estoque.application.dto.CriarSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.HistoricoSolicitacaoResponse;
import com.nexo.app.modules.estoque.application.dto.PlanejarSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.services.ConfiguracaoChavesAuditoriaSolicitacao;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.IntegridadeHistoricoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.IdempotenciaComandoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.PublicadorEventoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.ResolvedorAutorEventoSolicitacao;
import com.nexo.app.modules.estoque.application.services.SerializadorPayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.application.usecases.EstoqueObraProvisioningService;
import com.nexo.app.modules.estoque.application.usecases.HistoricoSolicitacaoUseCases;
import com.nexo.app.modules.estoque.application.usecases.ReferenciaTecnicaValidator;
import com.nexo.app.modules.estoque.application.usecases.SolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.domain.EscopoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.EventoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.StatusIntegridadeHistorico;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.EventoSolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.ItemSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.shared.domain.BaseEntity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Cobertura independente do contrato final do historico tecnico por solicitacao.
 *
 * <p>O gravador e a integridade sao reais nesta caracterizacao; somente as bordas
 * de persistencia e referencias externas sao simuladas para que a regressao nao
 * dependa dos testes das frentes A/B.</p>
 */
@ExtendWith(MockitoExtension.class)
class HistoricoSolicitacaoTecnicaRegressionTest {

    @Mock private ReferenciaTecnicaValidator referenciaValidator;
    @Mock private EstoqueObraProvisioningService estoqueObraProvisioningService;
    @Mock private EstoqueRepository estoqueRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private SolicitacaoMaterialRepository solicitacaoRepository;
    @Mock private ItemSolicitacaoRepository itemRepository;
    @Mock private PublicadorEventoSolicitacaoTecnica publicadorEventos;
    @Mock private ObservabilidadeSolicitacaoTecnica observabilidade;
    @Mock private IdempotenciaComandoSolicitacaoService idempotencia;

    @Test
    void criacaoEReplanejamentoGeramUmEventoPorComandoComDiffsDeDataMaterialEQuantidade()
            throws Exception {
        Cenario cenario = executarFluxo();

        assertThat(cenario.eventos()).hasSize(2);
        EventoSolicitacaoMaterial criacao = cenario.eventos().getFirst();
        EventoSolicitacaoMaterial replanejamento = cenario.eventos().getLast();

        assertThat(criacao.getTipoEvento()).isEqualTo(TipoEventoSolicitacao.SOLICITACAO_CRIADA);
        assertThat(criacao.getSequencia()).isEqualTo(1L);
        assertThat(criacao.getHashAnterior()).isNull();
        assertThat(criacao.getHashAtual()).isNotBlank();
        assertThat(criacao.getVersaoChave()).isEqualTo("v1");
        JsonNode payloadCriacao = cenario.objectMapper().readTree(criacao.getPayloadCanonico());
        assertThat(payloadCriacao.path("comando").asText()).isEqualTo("CRIAR_SOLICITACAO_TECNICA");
        assertThat(payloadCriacao.path("alteracoes")).isEmpty();
        assertThat(payloadCriacao.path("snapshots").path("obra").path("id").asText())
                .isEqualTo(cenario.idObra().toString());
        assertThat(payloadCriacao.path("snapshots").path("ocorrencia").path("snapshot").asText())
                .isEqualTo("OC-1042");
        assertThat(payloadCriacao.path("snapshots").path("itens").get(0).path("material").path("id").asText())
                .isEqualTo(cenario.idMaterialInicial().toString());
        assertThat(payloadCriacao.path("snapshots").path("itens").get(0).path("quantidade").asText())
                .isEqualTo("1.500");
        assertThat(replanejamento.getSequencia()).isEqualTo(2L);
        assertThat(replanejamento.getHashAnterior()).isEqualTo(criacao.getHashAtual());
        assertThat(replanejamento.getAutorSnapshot()).isEqualTo("Thalisson");
        assertThat(replanejamento.getOrigem()).isEqualTo(OrigemComandoTecnico.UI);
        assertThat(replanejamento.getJustificativa()).isEqualTo("ajuste de planejamento");

        JsonNode payload = cenario.objectMapper().readTree(replanejamento.getPayloadCanonico());
        assertThat(payload.path("comando").asText()).isEqualTo("REPLANEJAR_SOLICITACAO_TECNICA");
        assertThat(payload.path("alteracoes")).hasSize(3);

        assertThat(payload.path("alteracoes").findValuesAsText("caminho"))
                .containsExactly(
                        "dataPrevista", "itens[" + cenario.idItem() + "].material",
                        "itens[" + cenario.idItem() + "].quantidade");
        assertThat(payload.path("alteracoes").get(0).path("caminho").asText())
                .isEqualTo("dataPrevista");
        assertThat(payload.path("alteracoes").get(0).path("valorAnterior").isNull()).isTrue();
        assertThat(payload.path("alteracoes").get(0).path("valorNovo").asText())
                .isEqualTo("2026-09-01");

        JsonNode alteracaoMaterial = payload.path("alteracoes").get(1);
        assertThat(alteracaoMaterial.path("caminho").asText())
                .isEqualTo("itens[" + cenario.idItem() + "].material");
        assertThat(alteracaoMaterial.path("valorAnterior").path("id").asText())
                .isEqualTo(cenario.idMaterialInicial().toString());
        assertThat(alteracaoMaterial.path("valorAnterior").path("snapshot").asText())
                .isEqualTo("MAT-001 - Material inicial");
        assertThat(alteracaoMaterial.path("valorNovo").path("id").asText())
                .isEqualTo(cenario.idMaterialNovo().toString());
        assertThat(alteracaoMaterial.path("valorNovo").path("snapshot").asText())
                .isEqualTo("MAT-002 - Material novo");

        JsonNode alteracaoQuantidade = payload.path("alteracoes").get(2);
        assertThat(alteracaoQuantidade.path("caminho").asText())
                .isEqualTo("itens[" + cenario.idItem() + "].quantidade");
        assertThat(alteracaoQuantidade.path("valorAnterior").asText())
                .isEqualTo("1.500");
        assertThat(alteracaoQuantidade.path("valorNovo").asText())
                .isEqualTo("2.500");

        var integridade = cenario.integridade().verificar(cenario.eventos());
        assertThat(integridade.status()).isEqualTo(StatusIntegridadeHistorico.INTEGRO);
        assertThat(integridade.sequenciaInicial()).isEqualTo(1L);
        assertThat(integridade.sequenciaFinal()).isEqualTo(2L);
    }

    @Test
    void consultaExpõeSomenteCamposDoContratoEIntegridadeDaCadeiaDoPrimeiroEvento()
            throws Exception {
        Cenario cenario = executarFluxo();

        HistoricoSolicitacaoResponse resposta = cenario.historico().porSolicitacao(
                cenario.idSolicitacao(), null, PageRequest.of(0, 20));
        JsonNode json = cenario.objectMapper().valueToTree(resposta);

        assertThat(nomes(json)).containsExactlyInAnyOrder("eventos", "page", "integridade");
        JsonNode evento = json.path("eventos").get(0);
        assertThat(evento.path("sequencia").asLong()).isEqualTo(2L);
        assertThat(nomes(evento)).containsExactlyInAnyOrder(
                "id", "escopo", "idSolicitacao", "sequencia", "tipo", "tipoTecnico",
                "tipoObra", "ocorrencia", "autor", "origem", "justificativa", "versao",
                "ocorridoEm", "payload");
        assertThat(nomes(evento)).doesNotContain("hashAnterior", "hashAtual", "versaoChave");
        assertThat(nomes(evento.path("autor"))).containsExactlyInAnyOrder("id", "nome");
        assertThat(evento.path("autor").path("nome").asText()).isEqualTo("Thalisson");

        JsonNode alteracao = evento.path("payload").path("alteracoes").get(0);
        assertThat(nomes(alteracao)).containsExactlyInAnyOrder(
                "caminho", "valorAnterior", "valorNovo");
        assertThat(nomes(alteracao)).doesNotContain("campo", "valorAtual", "anterior", "novo");

        JsonNode cadeia = json.path("integridade").get(0);
        assertThat(nomes(cadeia)).containsExactlyInAnyOrder(
                "escopo", "idCadeia", "status", "sequenciaInicial", "sequenciaFinal");
        assertThat(cadeia.path("idCadeia").asText()).isEqualTo(cenario.idSolicitacao().toString());
        assertThat(cadeia.path("status").asText()).isEqualTo("INTEGRO");
        assertThat(cadeia.path("sequenciaInicial").asLong()).isEqualTo(1L);
        assertThat(cadeia.path("sequenciaFinal").asLong()).isEqualTo(2L);
        assertThat(resposta.eventos()).extracting(HistoricoSolicitacaoResponse.Evento::sequencia)
                .containsExactly(2L, 1L);
    }

    private Cenario executarFluxo() {
        UUID idObra = UUID.randomUUID();
        UUID idOcorrencia = UUID.randomUUID();
        UUID idAlmoxarifado = UUID.randomUUID();
        UUID idUsuario = UUID.randomUUID();
        UUID idMaterialInicial = UUID.randomUUID();
        UUID idMaterialNovo = UUID.randomUUID();
        ObraApi.ObraPublica obra = new ObraApi.ObraPublica(
                idObra, "OBRA-001", "Obra de regressao", ObraApi.TipoObraPublico.CORRETIVA,
                true, UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of());
        ObraApi.OcorrenciaPublica ocorrencia =
                new ObraApi.OcorrenciaPublica(idOcorrencia, idObra, "OC-1042");
        ReferenciaTecnicaValidator.ContextoTecnicoValidado contexto =
                new ReferenciaTecnicaValidator.ContextoTecnicoValidado(obra, ocorrencia, null, null);
        Material materialInicial = material(idMaterialInicial, "MAT-001", "Material inicial");
        Material materialNovo = material(idMaterialNovo, "MAT-002", "Material novo");
        Estoque almoxarifado = estoque(idAlmoxarifado, "ALMOX-REGRESSAO", "FISICO");
        Estoque estoqueObra = estoque(UUID.randomUUID(), "OBRA-REGRESSAO", "OBRA");
        estoqueObra.vincularObra(idObra);

        List<EventoSolicitacaoMaterial> eventos = new ArrayList<>();
        configurarRepositorioDeEventos(eventos);
        AtomicReference<SolicitacaoMaterial> persistida = new AtomicReference<>();
        when(solicitacaoRepository.findById(any(UUID.class)))
                .thenAnswer(invocacao -> Optional.ofNullable(persistida.get()));
        when(solicitacaoRepository.saveAll(any())).thenAnswer(invocacao -> {
            @SuppressWarnings("unchecked")
            List<SolicitacaoMaterial> solicitacoes = invocacao.getArgument(0);
            solicitacoes.forEach(solicitacao -> {
                comId(solicitacao);
                solicitacao.getItens().forEach(item -> comId(item));
                persistida.set(solicitacao);
            });
            return solicitacoes;
        });
        when(referenciaValidator.validar(
                eq(idObra), eq("OC-1042"), isNull(UUID.class), isNull(UUID.class), isNull(UUID.class), eq(false)))
                .thenReturn(contexto);
        when(estoqueRepository.findById(idAlmoxarifado)).thenReturn(Optional.of(almoxarifado));
        when(materialRepository.findAllById(any())).thenReturn(List.of(materialInicial, materialNovo));
        when(estoqueObraProvisioningService.resolverOuProvisionar(obra)).thenReturn(estoqueObra);

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ConfiguracaoChavesAuditoriaSolicitacao configuracao = new ConfiguracaoChavesAuditoriaSolicitacao();
        configuracao.setVersaoAtiva("v1");
        configuracao.setChaves(Map.of("v1", Base64.getEncoder().encodeToString(
                "chave-de-regressao-com-32-bytes-000".getBytes(StandardCharsets.UTF_8))));
        ObservabilidadeSolicitacaoTecnica metricas =
                new ObservabilidadeSolicitacaoTecnica(new SimpleMeterRegistry());
        IntegridadeHistoricoSolicitacaoService integridade =
                new IntegridadeHistoricoSolicitacaoService(configuracao, metricas);
        ResolvedorAutorEventoSolicitacao resolvedorAutor = org.mockito.Mockito.mock(
                ResolvedorAutorEventoSolicitacao.class);
        when(resolvedorAutor.resolver(any(), any())).thenReturn("Thalisson");
        GravadorEventoSolicitacaoService gravador = new GravadorEventoSolicitacaoService(
                eventoRepository, new SerializadorPayloadEventoSolicitacao(objectMapper), integridade,
                resolvedorAutor,
                clockForScenario());
        SolicitacaoTecnicaUseCases useCases = new SolicitacaoTecnicaUseCases(
                referenciaValidator, estoqueObraProvisioningService, estoqueRepository, materialRepository,
                solicitacaoRepository, itemRepository, gravador, publicadorEventos, observabilidade, idempotencia);

        useCases.criar(new CriarSolicitacaoTecnicaRequest(
                idObra, "OC-1042", idAlmoxarifado, null, null, "Original",
                List.of(new CriarSolicitacaoTecnicaRequest.Item(
                        idMaterialInicial, TipoSolicitacaoTecnica.ADICAO_COMUM,
                        new java.math.BigDecimal("1.500")))), idUsuario, OrigemComandoTecnico.UI);
        SolicitacaoMaterial solicitacao = persistida.get();
        comId(solicitacao);
        ReflectionTestUtils.setField(solicitacao, "versao", 0L);
        ItemSolicitacao item = solicitacao.getItens().getFirst();
        comId(item);

        useCases.replanejar(solicitacao.getId(), new PlanejarSolicitacaoTecnicaRequest(
                0L, idObra, "OC-1042", idAlmoxarifado, null, LocalDate.of(2026, 9, 1),
                "Original", List.of(new PlanejarSolicitacaoTecnicaRequest.Item(
                        item.getId(), idMaterialNovo, TipoSolicitacaoTecnica.ADICAO_COMUM,
                        new java.math.BigDecimal("2.500"))), "ajuste de planejamento",
                true, true, true, true), idUsuario,
                OrigemComandoTecnico.UI);

        HistoricoSolicitacaoUseCases historico = new HistoricoSolicitacaoUseCases(
                eventoRepository, integridade, resolvedorAutor, objectMapper);
        return new Cenario(solicitacao.getId(), idObra, item.getId(), idUsuario, idMaterialInicial,
                idMaterialNovo, eventos, integridade, objectMapper, historico);
    }

    @Mock private EventoSolicitacaoMaterialRepository eventoRepository;

    private void configurarRepositorioDeEventos(List<EventoSolicitacaoMaterial> eventos) {
        when(eventoRepository.findTopByEscopoAndIdSolicitacaoOrderBySequenciaDesc(
                any(EscopoEventoSolicitacao.class), any(UUID.class))).thenAnswer(invocacao -> eventos.stream()
                .filter(evento -> evento.getEscopo() == invocacao.getArgument(0)
                        && evento.getIdSolicitacao().equals(invocacao.getArgument(1)))
                .max(Comparator.comparingLong(EventoSolicitacaoMaterial::getSequencia)));
        when(eventoRepository.save(any(EventoSolicitacaoMaterial.class))).thenAnswer(invocacao -> {
            EventoSolicitacaoMaterial evento = invocacao.getArgument(0);
            if (evento.getId() == null) {
                ReflectionTestUtils.setField(evento, "id", UUID.randomUUID());
            }
            eventos.add(evento);
            return evento;
        });
        lenient().when(eventoRepository.findAllByEscopoAndIdSolicitacaoOrderBySequenciaAsc(
                any(EscopoEventoSolicitacao.class), any(UUID.class))).thenAnswer(invocacao -> eventos.stream()
                .filter(evento -> evento.getEscopo() == invocacao.getArgument(0)
                        && evento.getIdSolicitacao().equals(invocacao.getArgument(1)))
                .sorted(Comparator.comparingLong(EventoSolicitacaoMaterial::getSequencia))
                .toList());
        lenient().when(eventoRepository.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(invocacao -> {
            Pageable pageable = invocacao.getArgument(1);
            List<EventoSolicitacaoMaterial> ordenados = eventos.stream()
                    .sorted(Comparator.comparing(EventoSolicitacaoMaterial::getOcorridoEm)
                            .thenComparing(EventoSolicitacaoMaterial::getId).reversed())
                    .toList();
            return new PageImpl<>(ordenados, pageable, ordenados.size());
        });
    }

    private static Material material(UUID id, String codigo, String descricao) {
        Material material = new Material(codigo, descricao, "UN", "MATR", null, null, false, UUID.randomUUID());
        ReflectionTestUtils.setField(material, "id", id);
        return material;
    }

    private static Estoque estoque(UUID id, String nome, String tipoNome) {
        TipoEstoque tipo = new TipoEstoque(tipoNome);
        ReflectionTestUtils.setField(tipo, "id", UUID.randomUUID());
        Estoque estoque = new Estoque(nome, tipo);
        ReflectionTestUtils.setField(estoque, "id", id);
        return estoque;
    }

    private static <T extends BaseEntity> T comId(T entity) {
        if (entity.getId() == null) {
            ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        }
        return entity;
    }

    private static Set<String> nomes(JsonNode node) {
        Set<String> nomes = new HashSet<>();
        node.fieldNames().forEachRemaining(nomes::add);
        return nomes;
    }

    private static Clock clockForScenario() {
        return new Clock() {
            private final Instant inicio = Instant.parse("2026-08-28T12:00:00Z");
            private long segundos;

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return inicio.plusSeconds(segundos++);
            }
        };
    }

    private record Cenario(
            UUID idSolicitacao,
            UUID idObra,
            UUID idItem,
            UUID idUsuario,
            UUID idMaterialInicial,
            UUID idMaterialNovo,
            List<EventoSolicitacaoMaterial> eventos,
            IntegridadeHistoricoSolicitacaoService integridade,
            ObjectMapper objectMapper,
            HistoricoSolicitacaoUseCases historico
    ) {
    }
}
