package com.nexo.app.modules.sst.application.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.sst.domain.EventoInspecaoSeguranca;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoInspecaoSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.TipoInspecaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GravadorEventoInspecaoServiceTest {

    @Mock
    private EventoInspecaoSegurancaRepository repository;
    @Mock
    private TipoInspecaoRepository tipoInspecaoRepository;
    @Mock
    private FormularioInspecaoRepository formularioRepository;
    @Mock
    private ProcessoNegocioApi processoApi;
    @Mock
    private BaseOperacionalApi baseOperacionalApi;
    @Mock
    private VeiculoApi veiculoApi;
    @Mock
    private EquipeApi equipeApi;
    @Mock
    private ColaboradorApi colaboradorApi;
    @Mock
    private UsuarioApi usuarioApi;

    private GravadorEventoInspecaoService service;

    @BeforeEach
    void setUp() {
        service = new GravadorEventoInspecaoService(
                repository,
                new ObjectMapper(),
                tipoInspecaoRepository,
                formularioRepository,
                processoApi,
                baseOperacionalApi,
                veiculoApi,
                equipeApi,
                colaboradorApi,
                usuarioApi
        );
    }

    @Test
    @DisplayName("Canonicalização de JSON deve ordenar chaves alfabeticamente e de forma determinística")
    void deveCanonicalizarJsonComChavesOrdenadas() {
        String json1 = "{\"b\":1,\"a\":\"teste\",\"c\":[{\"y\":2,\"x\":1}]}";
        String json2 = "{\"a\":\"teste\",\"c\":[{\"x\":1,\"y\":2}],\"b\":1}";

        String canonico1 = GravadorEventoInspecaoService.canonicalizarJson(json1);
        String canonico2 = GravadorEventoInspecaoService.canonicalizarJson(json2);

        assertThat(canonico1).isEqualTo(canonico2);
        assertThat(canonico1).isEqualTo("{\"a\":\"teste\",\"b\":1,\"c\":[{\"x\":1,\"y\":2}]}");
    }

    @Test
    @DisplayName("Instante canônico deve converter para UTC e truncar em microssegundos")
    void deveNormalizarInstanteParaMicrossegundosUtc() {
        // Horário de Brasília com nanossegundos
        OffsetDateTime dt = OffsetDateTime.of(2026, 9, 28, 11, 47, 21, 537488999, ZoneOffset.ofHours(-3));
        String canonico = GravadorEventoInspecaoService.instanteCanonico(dt);

        assertThat(canonico).isEqualTo("2026-09-28T14:47:21.537488Z");
    }

    @Test
    @DisplayName("Cadeia vazia retorna NAO_VERIFICADO")
    void deveRetornarNaoVerificadoParaCadeiaVazia() {
        assertThat(service.verificarIntegridade(null)).isEqualTo("NAO_VERIFICADO");
        assertThat(service.verificarIntegridade(List.of())).isEqualTo("NAO_VERIFICADO");
    }

    @Test
    @DisplayName("Cadeia válida com hash legado deve auto-reparar para o canônico e retornar INTEGRO")
    void deveAutoRepararHashLegadoERetornarIntegro() {
        UUID idInspecao = UUID.randomUUID();
        OffsetDateTime dt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        String payload = "{\"comando\":\"EDITAR_CABECALHO\",\"alteracoes\":[]}";

        EventoInspecaoSeguranca evento = new EventoInspecaoSeguranca(
                idInspecao,
                1L,
                "EDICAO_CABECALHO",
                UUID.randomUUID(),
                "usradmin",
                "UI",
                "ajuste",
                payload,
                dt
        );
        // Sela com hash legado divergente
        evento.selar(null, "hash_antigo_ou_divergente_por_fuso_local");

        when(repository.save(any(EventoInspecaoSeguranca.class))).thenAnswer(inv -> inv.getArgument(0));

        String status = service.verificarIntegridade(List.of(evento));

        assertThat(status).isEqualTo("INTEGRO");
        verify(repository).save(evento);
        assertThat(evento.getHashAtual()).isNotEqualTo("hash_antigo_ou_divergente_por_fuso_local");
        assertThat(evento.getHashAtual()).hasSize(64); // SHA-256 hex
    }

    @Test
    @DisplayName("Cadeia com quebra de hash anterior deve retornar VIOLADO")
    void deveRetornarVioladoQuandoHashAnteriorNaoBate() {
        UUID idInspecao = UUID.randomUUID();
        OffsetDateTime dt1 = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        OffsetDateTime dt2 = dt1.plusSeconds(10);

        EventoInspecaoSeguranca evt1 = new EventoInspecaoSeguranca(
                idInspecao, 1L, "REGISTRO_INICIAL", UUID.randomUUID(), "usradmin", "UI", null, "{}", dt1
        );
        evt1.selar(null, "hash1");

        EventoInspecaoSeguranca evt2 = new EventoInspecaoSeguranca(
                idInspecao, 2L, "EDICAO_CABECALHO", UUID.randomUUID(), "usradmin", "UI", "justificativa", "{}", dt2
        );
        // Aponta para um hash anterior que não existe na cadeia
        evt2.selar("hash_invalido_adulterado", "hash2");

        String status = service.verificarIntegridade(List.of(evt1, evt2));

        assertThat(status).isEqualTo("VIOLADO");
        verify(repository, never()).save(evt2);
    }
}
