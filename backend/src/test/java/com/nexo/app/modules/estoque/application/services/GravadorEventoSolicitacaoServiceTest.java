package com.nexo.app.modules.estoque.application.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexo.app.modules.estoque.domain.EscopoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.EventoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.infrastructure.persistence.EventoSolicitacaoMaterialRepository;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.OcorrenciaPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import com.nexo.app.shared.domain.BaseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GravadorEventoSolicitacaoServiceTest {

    @Test
    void mantemSequenciasIndependentesESnapshotsNaCriacaoMistaEEdicao() {
        EventoSolicitacaoMaterialRepository repository = mock(EventoSolicitacaoMaterialRepository.class);
        List<EventoSolicitacaoMaterial> persistidos = new ArrayList<>();
        when(repository.findTopByEscopoAndIdSolicitacaoOrderBySequenciaDesc(any(), any()))
                .thenAnswer(invocation -> ultimo(
                        persistidos, EscopoEventoSolicitacao.SOLICITACAO, invocation.getArgument(1)));
        when(repository.save(any())).thenAnswer(invocation -> {
            EventoSolicitacaoMaterial evento = invocation.getArgument(0);
            persistidos.add(evento);
            return evento;
        });
        ConfiguracaoChavesAuditoriaSolicitacao chaves = new ConfiguracaoChavesAuditoriaSolicitacao();
        chaves.setVersaoAtiva("v1");
        chaves.setChaves(Map.of("v1", Base64.getEncoder().encodeToString(
                "s".repeat(32).getBytes(StandardCharsets.UTF_8))));
        SerializadorPayloadEventoSolicitacao serializador = new SerializadorPayloadEventoSolicitacao(
                new ObjectMapper().registerModule(new JavaTimeModule()));
        ResolvedorAutorEventoSolicitacao resolvedorAutor = mock(ResolvedorAutorEventoSolicitacao.class);
        when(resolvedorAutor.resolver(any(), any())).thenAnswer(invocacao -> invocacao.getArgument(1));
        GravadorEventoSolicitacaoService gravador = new GravadorEventoSolicitacaoService(
                repository, serializador, new IntegridadeHistoricoSolicitacaoService(
                        chaves, new ObservabilidadeSolicitacaoTecnica(new SimpleMeterRegistry())),
                resolvedorAutor,
                Clock.fixed(Instant.parse("2026-08-21T15:00:00Z"), ZoneOffset.UTC));
        Fixture f = fixture();
        ContextoRegistroEventoSolicitacao contexto = new ContextoRegistroEventoSolicitacao(
                UUID.randomUUID(), "Usuario Teste", OrigemComandoTecnico.UI, null);

        gravador.registrarFilho(
                f.filho221(), TipoEventoSolicitacao.SOLICITACAO_CRIADA,
                PayloadEventoSolicitacao.semAlteracoes("CRIAR", Map.of(
                        "token", "token-que-nao-pode-ser-persistido",
                        "dados", Map.of("assinaturaEquipe", "assinatura-em-claro", "visivel", "ok"))), contexto);
        gravador.registrarFilho(
                f.filho921(), TipoEventoSolicitacao.SOLICITACAO_CRIADA,
                PayloadEventoSolicitacao.semAlteracoes("CRIAR_921", Map.of()), contexto);
        gravador.registrarFilho(
                f.filho221(), TipoEventoSolicitacao.PLANEJAMENTO_ALTERADO,
                new PayloadEventoSolicitacao("PLANEJAR", List.of(
                        new com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao(
                                "codigoOcorrenciaSnapshot", "OC-1", "OC-2"),
                        new com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao(
                                "dataPrevista", null, java.time.LocalDate.of(2026, 8, 22))), Map.of()),
                contexto);

        assertThat(persistidos).extracting(EventoSolicitacaoMaterial::getSequencia)
                .containsExactly(1L, 1L, 2L);
        assertThat(persistidos).allSatisfy(evento -> {
            assertThat(evento.getEscopo()).isEqualTo(EscopoEventoSolicitacao.SOLICITACAO);
            assertThat(evento.getIdSolicitacao()).isNotNull();
        });
        assertThat(persistidos.get(0).getPayloadCanonico())
                .contains("obra", "almoxarifado", "usuario", "visivel", "ok")
                .doesNotContain("token", "token-que-nao-pode-ser-persistido",
                        "assinaturaEquipe", "assinatura-em-claro");
        assertThat(persistidos.get(0).getHashAtual())
                .doesNotContain("token-que-nao-pode-ser-persistido", "assinatura-em-claro");
        assertThat(persistidos.get(1).getPayloadCanonico())
                .contains("itens", "material", "tipoTecnico");
        assertThat(persistidos.get(2).getPayloadCanonico())
                .contains("codigoOcorrenciaSnapshot", "dataPrevista");
    }

    private static Optional<EventoSolicitacaoMaterial> ultimo(
            List<EventoSolicitacaoMaterial> eventos,
            EscopoEventoSolicitacao escopo,
            UUID id
    ) {
        return eventos.stream()
                .filter(evento -> evento.getEscopo() == escopo)
                .filter(evento -> id.equals(evento.getIdSolicitacao()))
                .max(Comparator.comparingLong(EventoSolicitacaoMaterial::getSequencia));
    }

    private static Fixture fixture() {
        ObraPublica obra = new ObraPublica(
                UUID.randomUUID(), "001", "OBRA TESTE", TipoObraPublico.CORRETIVA, true,
                UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of());
        Estoque almoxarifado = estoque("ALMOXARIFADO", "FISICO", obra);
        Estoque estoqueObra = estoque("OBRA 001", "OBRA", obra);
        estoqueObra.vincularObra(obra.id());
        SolicitacaoMaterial filho221 = filho(
                obra, new OcorrenciaPublica(UUID.randomUUID(), obra.id(), "OC-1"),
                TipoSolicitacaoTecnica.ADICAO_COMUM, estoqueObra, almoxarifado,
                material("COMUM"));
        SolicitacaoMaterial filho921 = filho(
                obra, new OcorrenciaPublica(UUID.randomUUID(), obra.id(), "OC-1"),
                TipoSolicitacaoTecnica.ADICAO_CONCRETO, estoqueObra, almoxarifado,
                material("CONCRETO"));
        return new Fixture(filho221, filho921);
    }

    private static SolicitacaoMaterial filho(
            ObraPublica obra,
            OcorrenciaPublica ocorrencia,
            TipoSolicitacaoTecnica tipo,
            Estoque estoqueObra,
            Estoque almoxarifado,
            Material material
    ) {
        SolicitacaoMaterial filho = comId(SolicitacaoMaterial.criarTecnica(
                obra, ocorrencia, tipo, estoqueObra, almoxarifado, null, null, null, UUID.randomUUID()));
        filho.adicionarItemTecnico(material, BigDecimal.ONE);
        filho.getItens().forEach(GravadorEventoSolicitacaoServiceTest::comId);
        return filho;
    }

    private static Material material(String codigo) {
        return comId(new Material(
                codigo, "Material " + codigo, "UN", "MATR", null, null, false,
                UUID.randomUUID()));
    }

    private static Estoque estoque(String nome, String tipo, ObraPublica obra) {
        Estoque estoque = comId(new Estoque(nome, comId(new TipoEstoque(tipo))));
        estoque.vincularBaseOperacional(obra.idBaseOperacional());
        return estoque;
    }

    private static <T extends BaseEntity> T comId(T entidade) {
        if (entidade.getId() == null) {
            ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        }
        return entidade;
    }

    private record Fixture(
            SolicitacaoMaterial filho221,
            SolicitacaoMaterial filho921
    ) {
    }
}
