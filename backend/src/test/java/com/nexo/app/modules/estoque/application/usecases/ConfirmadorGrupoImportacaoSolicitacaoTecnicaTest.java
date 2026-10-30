package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.CriacaoSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.dto.PreviaImportacaoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.LoteImportacaoSolicitacao;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.infrastructure.persistence.LoteImportacaoSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmadorGrupoImportacaoSolicitacaoTecnicaTest {

    @Mock private LoteImportacaoSolicitacaoRepository loteRepository;
    @Mock private SolicitacaoTecnicaUseCases solicitacaoUseCases;
    @Mock private ReservaSolicitacaoTecnicaUseCases reservaUseCases;
    @Mock private SolicitacaoMaterialRepository solicitacaoRepository;
    @Mock private GravadorEventoSolicitacaoService gravadorEvento;
    @Mock private ObservabilidadeSolicitacaoTecnica observabilidade;
    private ConfirmadorGrupoImportacaoSolicitacaoTecnica confirmador;

    @BeforeEach
    void setUp() {
        confirmador = new ConfirmadorGrupoImportacaoSolicitacaoTecnica(
                loteRepository, solicitacaoUseCases, reservaUseCases,
                solicitacaoRepository, gravadorEvento, observabilidade);
    }

    @Test
    void confirmaCombinacaoChaveTipoERepeteSemDuplicar() {
        UUID idUsuario = UUID.randomUUID();
        UUID idSolicitacao = UUID.randomUUID();
        LoteImportacaoSolicitacao lote = lote(idUsuario, false);
        when(loteRepository.buscarParaConfirmacao("token-hash", idUsuario)).thenReturn(Optional.of(lote));
        when(solicitacaoUseCases.criar(any(), eq(idUsuario), eq(OrigemComandoTecnico.IMPORT)))
                .thenReturn(new CriacaoSolicitacaoTecnicaResponse(List.of(
                        new CriacaoSolicitacaoTecnicaResponse.SolicitacaoCriada(
                                TipoSolicitacaoTecnica.ADICAO_COMUM, idSolicitacao))));
        SolicitacaoMaterial solicitacao = mock(SolicitacaoMaterial.class);
        when(solicitacaoRepository.findById(idSolicitacao)).thenReturn(Optional.of(solicitacao));
        OffsetDateTime agora = OffsetDateTime.now();

        var primeira = confirmador.confirmar(
                "token-hash", "SOL-1", "221", true, idUsuario, "key-hash", agora);
        var repetida = confirmador.confirmar(
                "token-hash", "SOL-1", "221", true, idUsuario, "key-hash", agora.plusSeconds(1));

        assertThat(primeira.tipo()).isEqualTo("221");
        assertThat(primeira.solicitacoes()).singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo(idSolicitacao));
        assertThat(repetida).isEqualTo(primeira);
        verify(solicitacaoUseCases, times(1))
                .criar(any(), eq(idUsuario), eq(OrigemComandoTecnico.IMPORT));
        var payloadCaptor = org.mockito.ArgumentCaptor.forClass(com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao.class);
        verify(gravadorEvento).registrarFilho(
                eq(solicitacao), eq(TipoEventoSolicitacao.IMPORTACAO_CONFIRMADA), payloadCaptor.capture(), any());
        var serializador = new com.nexo.app.modules.estoque.application.services.SerializadorPayloadEventoSolicitacao(
                new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> serializador.serializar(payloadCaptor.getValue()));
    }

    @Test
    void confirmaCombinacaoPassandoNumeroObraComoChave() {
        UUID idUsuario = UUID.randomUUID();
        UUID idSolicitacao = UUID.randomUUID();
        LoteImportacaoSolicitacao lote = lote(idUsuario, false);
        when(loteRepository.buscarParaConfirmacao("token-hash", idUsuario)).thenReturn(Optional.of(lote));
        when(solicitacaoUseCases.criar(any(), eq(idUsuario), eq(OrigemComandoTecnico.IMPORT)))
                .thenReturn(new CriacaoSolicitacaoTecnicaResponse(List.of(
                        new CriacaoSolicitacaoTecnicaResponse.SolicitacaoCriada(
                                TipoSolicitacaoTecnica.ADICAO_COMUM, idSolicitacao))));
        SolicitacaoMaterial solicitacao = mock(SolicitacaoMaterial.class);
        when(solicitacaoRepository.findById(idSolicitacao)).thenReturn(Optional.of(solicitacao));

        var resultado = confirmador.confirmar(
                "token-hash", "OBRA-1", "221", true, idUsuario, "key-hash", OffsetDateTime.now());

        assertThat(resultado.chave()).isEqualTo("OBRA-1");
        assertThat(resultado.tipo()).isEqualTo("221");
        assertThat(resultado.solicitacoes()).singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo(idSolicitacao));
    }

    @Test
    void confirmaCombinacao921QuandoExistemMultiplosTiposParaMesmaObra() {
        UUID idUsuario = UUID.randomUUID();
        UUID idSolicitacao = UUID.randomUUID();
        LoteImportacaoSolicitacao lote = loteComMultiplosTipos(idUsuario);
        when(loteRepository.buscarParaConfirmacao("token-hash", idUsuario)).thenReturn(Optional.of(lote));
        when(solicitacaoUseCases.criar(any(), eq(idUsuario), eq(OrigemComandoTecnico.IMPORT)))
                .thenReturn(new CriacaoSolicitacaoTecnicaResponse(List.of(
                        new CriacaoSolicitacaoTecnicaResponse.SolicitacaoCriada(
                                TipoSolicitacaoTecnica.ADICAO_CONCRETO, idSolicitacao))));
        SolicitacaoMaterial solicitacao = mock(SolicitacaoMaterial.class);
        when(solicitacaoRepository.findById(idSolicitacao)).thenReturn(Optional.of(solicitacao));

        var resultado = confirmador.confirmar(
                "token-hash", "OBRA-1", "921", true, idUsuario, "key-hash", OffsetDateTime.now());

        assertThat(resultado.chave()).isEqualTo("OBRA-1");
        assertThat(resultado.tipo()).isEqualTo("921");
        assertThat(resultado.solicitacoes()).singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo(idSolicitacao));
    }

    @Test
    void rejeitaOutraChaveIdempotenteNaMesmaCombinacao() {
        UUID idUsuario = UUID.randomUUID();
        LoteImportacaoSolicitacao lote = lote(idUsuario, false);
        var resultado = new PreviaImportacaoSolicitacaoTecnica.ResultadoConfirmacao(
                List.of(new PreviaImportacaoSolicitacaoTecnica.SolicitacaoCriada(
                        "221", UUID.randomUUID())),
                "key-original", OffsetDateTime.now(), "221");
        lote.registrarResultado("SOL-1", "221", resultado, idUsuario, OffsetDateTime.now());
        when(loteRepository.buscarParaConfirmacao("token-hash", idUsuario)).thenReturn(Optional.of(lote));

        assertThatThrownBy(() -> confirmador.confirmar(
                "token-hash", "SOL-1", "221", true, idUsuario,
                "outra-key", OffsetDateTime.now()))
                .hasMessageContaining("outra chave de idempotencia");

        verify(solicitacaoUseCases, never()).criar(any(), any(), any());
    }

    @Test
    void avisoExigeReconhecimentoAntesDaCriacao() {
        UUID idUsuario = UUID.randomUUID();
        when(loteRepository.buscarParaConfirmacao("token-hash", idUsuario))
                .thenReturn(Optional.of(lote(idUsuario, true)));

        assertThatThrownBy(() -> confirmador.confirmar(
                "token-hash", "SOL-1", "221", false, idUsuario,
                "key-hash", OffsetDateTime.now()))
                .hasMessageContaining("Reconheca os avisos");

        verify(solicitacaoUseCases, never()).criar(any(), any(), any());
    }

    private LoteImportacaoSolicitacao lote(UUID idUsuario, boolean aviso) {
        List<PreviaImportacaoSolicitacaoTecnica.Mensagem> mensagens = aviso
                ? List.of(new PreviaImportacaoSolicitacaoTecnica.Mensagem(
                        null, "Codigo Material", "MATERIAL_DUPLICADO_CONSOLIDADO",
                        PreviaImportacaoSolicitacaoTecnica.Severidade.AVISO,
                        "MAT-1", "Aviso"))
                : List.of();
        var prevista = new PreviaImportacaoSolicitacaoTecnica.SolicitacaoPrevista(
                "SOL-1", UUID.randomUUID(), "OBRA-1", TipoObraSnapshot.OBRA, null,
                UUID.randomUUID(), "ALMOX", null, null, null, null,
                List.of(new PreviaImportacaoSolicitacaoTecnica.Subgrupo(
                        "221", List.of(new PreviaImportacaoSolicitacaoTecnica.Item(
                                UUID.randomUUID(), "MAT-1", "Material",
                                BigDecimal.ONE, false, List.of(2))),
                        null, List.of(2))),
                mensagens, null);
        LoteImportacaoSolicitacao lote = new LoteImportacaoSolicitacao(
                "a".repeat(64), "b".repeat(64), "arquivo.csv",
                LoteImportacaoSolicitacao.Formato.CSV,
                new PreviaImportacaoSolicitacaoTecnica(List.of(prevista)),
                1, idUsuario, OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now().plusMinutes(29));
        ReflectionTestUtils.setField(lote, "id", UUID.randomUUID());
        return lote;
    }

    private LoteImportacaoSolicitacao loteComMultiplosTipos(UUID idUsuario) {
        var prevista221 = new PreviaImportacaoSolicitacaoTecnica.SolicitacaoPrevista(
                "OBRA-1 - 221 - 2026-10-01", UUID.randomUUID(), "OBRA-1", TipoObraSnapshot.OBRA, null,
                UUID.randomUUID(), "ALMOX", null, null, null, null,
                List.of(new PreviaImportacaoSolicitacaoTecnica.Subgrupo(
                        "221", List.of(new PreviaImportacaoSolicitacaoTecnica.Item(
                                UUID.randomUUID(), "MAT-1", "Material",
                                BigDecimal.ONE, false, List.of(2))),
                        null, List.of(2))),
                List.of(), null);
        var prevista921 = new PreviaImportacaoSolicitacaoTecnica.SolicitacaoPrevista(
                "OBRA-1 - 921 - 2026-10-01", UUID.randomUUID(), "OBRA-1", TipoObraSnapshot.OBRA, null,
                UUID.randomUUID(), "ALMOX", null, null, null, null,
                List.of(new PreviaImportacaoSolicitacaoTecnica.Subgrupo(
                        "921", List.of(new PreviaImportacaoSolicitacaoTecnica.Item(
                                UUID.randomUUID(), "MAT-2", "Material Concreto",
                                BigDecimal.valueOf(5), false, List.of(3))),
                        null, List.of(3))),
                List.of(), null);
        LoteImportacaoSolicitacao lote = new LoteImportacaoSolicitacao(
                "a".repeat(64), "b".repeat(64), "arquivo.csv",
                LoteImportacaoSolicitacao.Formato.CSV,
                new PreviaImportacaoSolicitacaoTecnica(List.of(prevista221, prevista921)),
                2, idUsuario, OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now().plusMinutes(29));
        ReflectionTestUtils.setField(lote, "id", UUID.randomUUID());
        return lote;
    }
}
